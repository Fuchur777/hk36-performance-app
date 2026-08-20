package nl.schellenberg.hk36ttc.data.catalog

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.schellenberg.hk36ttc.core.airport.ParsedAirport
import nl.schellenberg.hk36ttc.core.airport.ParsedRunway
import nl.schellenberg.hk36ttc.core.airport.ParsedRunwaySurface
import nl.schellenberg.hk36ttc.core.airport.forEachAirport
import nl.schellenberg.hk36ttc.core.airport.forEachRunway
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.AirfieldEntity
import nl.schellenberg.hk36ttc.data.local.RunwayStripEntity
import nl.schellenberg.hk36ttc.data.local.RunwaySurfaceType

/** How far a long-running catalogue operation has got, for the progress UI. */
data class CatalogProgress(val airportsDone: Int, val runwaysDone: Int)

/** Outcome of pulling runways into one airfield. */
sealed interface RunwayImportResult {
    /** The airfield already had runways of its own, so nothing was touched. */
    data object AlreadyHasRunways : RunwayImportResult

    /** The catalogue has no usable runways for this airfield. */
    data object NothingAvailable : RunwayImportResult

    /**
     * [imported] strips were added, of which [withDerivedHeading] carry a heading
     * reconstructed from the runway number rather than published by the source — the pilot
     * must be told to check those.
     */
    data class Imported(val imported: Int, val withDerivedHeading: Int) : RunwayImportResult
}

/** Outcome of refreshing the pilot's own airfields against the catalogue. */
data class AirfieldRefreshResult(val updated: Int, val checked: Int)

/**
 * Loads, refreshes and searches the OurAirports reference catalogue, and copies entries out of
 * it into the pilot's own airfields.
 *
 * Kept separate from [AircraftProfileRepository] rather than folded into it: that class already
 * takes eleven DAOs positionally and every addition forces a matching edit in the test fakes,
 * and keeping the catalogue at arm's length mirrors in code the separation that exists in the
 * database files.
 *
 * **The one rule this class exists to honour**: it reaches the pilot's data only through
 * [userRepository]'s public API, and only ever *adds*. It never calls `deleteRunwayStrip` or
 * `deleteAirfieldCascade`, and [importRunwaysForAirfield] refuses outright on any airfield that
 * already has runways of its own.
 */
class AirportCatalogRepository(
    private val airportDao: AirportCatalogDao,
    private val runwayDao: RunwayCatalogDao,
    private val metaDao: CatalogMetaDao,
    private val userRepository: AircraftProfileRepository,
    /** Opens a bundled asset by path. A lambda rather than an Android `Context` so the whole
     * class stays testable on the JVM — the rule this class enforces is worth a real test. */
    private val openAsset: (String) -> InputStream,
    /** Runs [block] in one catalogue-database transaction. Injected for the same reason. */
    private val transaction: suspend (suspend () -> Unit) -> Unit,
    /** A scratch file in the app's cache, by name. Used to stage a download before it is parsed;
     * injected for the same JVM-testability reason as [openAsset]. */
    private val newTempFile: (String) -> java.io.File
) {

    // --- Loading -----------------------------------------------------------------------

    suspend fun isEmpty(): Boolean = withContext(Dispatchers.IO) { airportDao.count() == 0 }

    suspend fun meta(): CatalogMetaEntity? = withContext(Dispatchers.IO) { metaDao.get() }

    /**
     * Fills the catalogue from the CSVs bundled in the APK, but only when it is empty — this is
     * the first-run path and is safe to call every time the search screen opens.
     *
     * Seeding runs on demand rather than at startup on purpose: ~120,000 rows take seconds to
     * insert, and the app's launch path (see `CalculationDataGate` in `MainActivity`) must stay
     * quick for a pilot who only wants a weight-and-balance sum.
     */
    suspend fun seedFromAssetsIfEmpty(onProgress: (CatalogProgress) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            if (airportDao.count() > 0) return@withContext false
            replaceCatalog(
                openAirports = { openAsset(ASSET_AIRPORTS) },
                openRunways = { openAsset(ASSET_RUNWAYS) },
                source = CatalogSource.BUNDLED,
                onProgress = onProgress
            )
            true
        }

    /**
     * Replaces the catalogue with today's data from OurAirports.
     *
     * Both files are downloaded to cache **before** the transaction opens. That ordering matters
     * twice over: a dropped connection halfway through leaves the existing catalogue untouched
     * rather than half-erased, and a slow mobile signal never holds a write transaction open for
     * minutes on end. Parsing then streams off local disk straight into the insert.
     */
    suspend fun refreshFromNetwork(onProgress: (CatalogProgress) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val airportsFile = downloadToCache(URL_AIRPORTS, TEMP_AIRPORTS)
            val runwaysFile = try {
                downloadToCache(URL_RUNWAYS, TEMP_RUNWAYS)
            } catch (e: Throwable) {
                airportsFile.delete()
                throw e
            }
            try {
                replaceCatalog(
                    openAirports = { airportsFile.inputStream() },
                    openRunways = { runwaysFile.inputStream() },
                    source = CatalogSource.DOWNLOADED,
                    onProgress = onProgress
                )
            } finally {
                airportsFile.delete()
                runwaysFile.delete()
            }
        }

    private fun downloadToCache(url: String, fileName: String): java.io.File {
        val target = newTempFile(fileName)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("HTTP ${connection.responseCode} bij $url")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Throwable) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
        return target
    }

    /**
     * Swaps in a new catalogue. The clear-and-insert runs inside one transaction so the app can
     * never observe a half-populated catalogue, and the meta row is written last so its counts
     * always describe what is actually stored.
     *
     * Rows are parsed and inserted in [INSERT_CHUNK]-sized batches as they stream off disk, so
     * peak memory is one chunk rather than all ~127,000 parsed rows at once — the whole reason
     * the core parsers expose a streaming form.
     */
    private suspend fun replaceCatalog(
        openAirports: () -> InputStream,
        openRunways: () -> InputStream,
        source: CatalogSource,
        onProgress: (CatalogProgress) -> Unit
    ) {
        var airportsDone = 0
        var runwaysDone = 0
        transaction {
            airportDao.clear()
            runwayDao.clear()

            val airportBuffer = ArrayList<AirportCatalogEntity>(INSERT_CHUNK)
            openAirports().bufferedReader().use { reader ->
                forEachAirport(reader.lineSequence()) { parsed ->
                    airportBuffer += parsed.toEntity()
                    if (airportBuffer.size >= INSERT_CHUNK) {
                        airportDao.insertAll(airportBuffer)
                        airportsDone += airportBuffer.size
                        airportBuffer.clear()
                        onProgress(CatalogProgress(airportsDone, runwaysDone))
                    }
                }
            }
            if (airportBuffer.isNotEmpty()) {
                airportDao.insertAll(airportBuffer)
                airportsDone += airportBuffer.size
            }
            onProgress(CatalogProgress(airportsDone, runwaysDone))

            val runwayBuffer = ArrayList<RunwayCatalogEntity>(INSERT_CHUNK)
            openRunways().bufferedReader().use { reader ->
                forEachRunway(reader.lineSequence()) { parsed ->
                    runwayBuffer += parsed.toEntity()
                    if (runwayBuffer.size >= INSERT_CHUNK) {
                        runwayDao.insertAll(runwayBuffer)
                        runwaysDone += runwayBuffer.size
                        runwayBuffer.clear()
                        onProgress(CatalogProgress(airportsDone, runwaysDone))
                    }
                }
            }
            if (runwayBuffer.isNotEmpty()) {
                runwayDao.insertAll(runwayBuffer)
                runwaysDone += runwayBuffer.size
            }
            onProgress(CatalogProgress(airportsDone, runwaysDone))

            metaDao.upsert(
                CatalogMetaEntity(
                    source = source.name,
                    loadedAtEpochMs = System.currentTimeMillis(),
                    airportCount = airportsDone,
                    runwayCount = runwaysDone
                )
            )
        }
    }

    // --- Searching ---------------------------------------------------------------------

    /**
     * Ranked lookup for the search screen. Blank queries return nothing rather than the first
     * fifty airports alphabetically, which would just be noise.
     */
    suspend fun search(query: String, limit: Int = 50): List<AirportCatalogEntity> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            airportDao.search(
                exact = trimmed.uppercase(),
                prefix = "$trimmed%",
                contains = "%$trimmed%",
                limit = limit
            )
        }
    }

    // --- Copying into the pilot's own data ---------------------------------------------

    /**
     * Creates one of the pilot's airfields from a catalogue entry and returns its new id. Only
     * the fields the source actually knows are filled; the METAR station is left blank so the
     * existing fallback to the ICAO code applies, and an unknown elevation becomes 0 for the
     * pilot to correct — flagged in the UI rather than silently trusted.
     */
    suspend fun createAirfieldFrom(entry: AirportCatalogEntity): Long {
        val airfield = AirfieldEntity(
            name = entry.name,
            icao = entry.icaoCode ?: entry.gpsCode ?: entry.localCode,
            metarStationIcao = null,
            elevationM = entry.elevationM ?: 0.0,
            // A field the source has no elevation for keeps the 0.0 placeholder, but is marked
            // so the editor can insist on a real value before it reaches a calculation.
            elevationKnown = entry.elevationM != null,
            metarRaw = null,
            metarEnteredAtEpochMs = null
        )
        return userRepository.saveAirfield(airfield)
    }

    /**
     * Copies the catalogue's runways into one airfield — **only when that airfield has none of
     * its own**. This is the guard behind the feature's hard requirement: existing runway data
     * is never deleted, never updated, never merged with. All or nothing, per airfield.
     *
     * [catalogIdent] is the OurAirports `ident` to read from; for an airfield created via
     * [createAirfieldFrom] that is the entry's own ident, and for a hand-entered one it is
     * whatever its ICAO code matches.
     */
    suspend fun importRunwaysForAirfield(airfieldId: Long, catalogIdent: String): RunwayImportResult =
        withContext(Dispatchers.IO) {
            if (userRepository.getRunwayStrips(airfieldId).isNotEmpty()) {
                return@withContext RunwayImportResult.AlreadyHasRunways
            }
            val available = runwayDao.forAirport(catalogIdent)
            if (available.isEmpty()) return@withContext RunwayImportResult.NothingAvailable

            available.forEach { row ->
                userRepository.saveRunwayStrip(
                    RunwayStripEntity(
                        airfieldId = airfieldId,
                        designatorA = row.designatorA,
                        // The app's own entity keeps this non-null and ignores it entirely when
                        // oneWay is set (see RunwayStripEntity's KDoc).
                        designatorB = row.designatorB ?: "",
                        headingDegTrueA = row.headingDegTrueA,
                        lengthM = row.lengthM,
                        surface = row.surface,
                        // No public dataset publishes runway slope; 0 matches both the app's
                        // existing default and AIC P173's "flat unless stated".
                        slopePctA = 0.0,
                        oneWay = row.oneWay
                    )
                )
            }
            RunwayImportResult.Imported(
                imported = available.size,
                withDerivedHeading = available.count { it.headingDerived }
            )
        }

    /** The catalogue ident matching one of the pilot's airfields, or null when it has no code
     * at all or the code is unknown to the source. Matches on any of the source's code columns
     * — see [AirportCatalogDao.findByCode], most gliding sites have no ICAO code. */
    suspend fun catalogIdentFor(airfield: AirfieldEntity): String? = withContext(Dispatchers.IO) {
        val code = airfield.icao?.trim()?.uppercase()?.ifBlank { null } ?: return@withContext null
        airportDao.findByCode(code)?.ident
    }

    /**
     * Refreshes the pilot's own airfields against the catalogue: name and elevation only.
     *
     * Everything else is deliberately left alone — the METAR station they chose (often a
     * *different* field, e.g. Terlet reading EHDL), the METAR text they pasted, and their
     * runways, which this method does not so much as read.
     */
    suspend fun updateAirfieldsFromCatalog(airfields: List<AirfieldEntity>): AirfieldRefreshResult =
        withContext(Dispatchers.IO) {
            var updated = 0
            var checked = 0
            airfields.forEach { airfield ->
                val code = airfield.icao?.trim()?.uppercase()?.ifBlank { null } ?: return@forEach
                checked++
                val entry = airportDao.findByCode(code) ?: return@forEach
                val catalogElevation = entry.elevationM
                val newElevation = catalogElevation ?: airfield.elevationM
                // A published elevation also settles the "is this a placeholder?" question; an
                // airfield the pilot already filled in stays known either way.
                val newElevationKnown = airfield.elevationKnown || catalogElevation != null
                if (entry.name == airfield.name &&
                    newElevation == airfield.elevationM &&
                    newElevationKnown == airfield.elevationKnown
                ) {
                    return@forEach
                }
                userRepository.saveAirfield(
                    airfield.copy(
                        name = entry.name,
                        elevationM = newElevation,
                        elevationKnown = newElevationKnown
                    )
                )
                updated++
            }
            AirfieldRefreshResult(updated = updated, checked = checked)
        }

    private fun ParsedAirport.toEntity() = AirportCatalogEntity(
        ident = ident,
        type = type,
        name = name,
        elevationM = elevationM,
        isoCountry = isoCountry,
        municipality = municipality,
        icaoCode = icaoCode,
        gpsCode = gpsCode,
        localCode = localCode,
        latitudeDeg = latitudeDeg,
        longitudeDeg = longitudeDeg
    )

    private fun ParsedRunway.toEntity() = RunwayCatalogEntity(
        airportIdent = airportIdent,
        designatorA = designatorA,
        designatorB = designatorB,
        headingDegTrueA = headingDegTrueA,
        headingDerived = headingDerived,
        lengthM = lengthM,
        surface = when (surface) {
            ParsedRunwaySurface.ASPHALT -> RunwaySurfaceType.ASPHALT
            ParsedRunwaySurface.GRASS -> RunwaySurfaceType.GRASS
        }.name,
        oneWay = oneWay
    )

    private companion object {
        const val ASSET_AIRPORTS = "data/airports.csv"
        const val ASSET_RUNWAYS = "data/runways.csv"
        const val URL_AIRPORTS = "https://davidmegginson.github.io/ourairports-data/airports.csv"
        const val URL_RUNWAYS = "https://davidmegginson.github.io/ourairports-data/runways.csv"

        /** Room binds every row's arguments in one statement; chunking keeps that bounded. */
        const val INSERT_CHUNK = 2_000

        /** Staging names for a network refresh; deleted again as soon as the swap commits. */
        const val TEMP_AIRPORTS = "catalog-airports.csv"
        const val TEMP_RUNWAYS = "catalog-runways.csv"
    }
}
