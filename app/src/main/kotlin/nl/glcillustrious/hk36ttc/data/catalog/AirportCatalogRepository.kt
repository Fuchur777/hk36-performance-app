package nl.glcillustrious.hk36ttc.data.catalog

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.glcillustrious.hk36ttc.core.airport.ParsedAirport
import nl.glcillustrious.hk36ttc.core.airport.ParsedRunway
import nl.glcillustrious.hk36ttc.core.airport.ParsedRunwaySurface
import nl.glcillustrious.hk36ttc.core.airport.parseAirportsCsv
import nl.glcillustrious.hk36ttc.core.airport.parseRunwaysCsv
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.RunwaySurfaceType

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
    private val transaction: suspend (suspend () -> Unit) -> Unit
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
                airports = { openAsset(ASSET_AIRPORTS).bufferedReader().useLines(::parseAirportsCsv) },
                runways = { openAsset(ASSET_RUNWAYS).bufferedReader().useLines(::parseRunwaysCsv) },
                source = CatalogSource.BUNDLED,
                onProgress = onProgress
            )
            true
        }

    /**
     * Replaces the catalogue with today's data from OurAirports. Both files are fully parsed
     * *before* anything is cleared, so a dropped connection halfway through leaves the existing
     * catalogue exactly as it was rather than half-erased.
     */
    suspend fun refreshFromNetwork(onProgress: (CatalogProgress) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val airports = downloadLines(URL_AIRPORTS) { parseAirportsCsv(it) }
            val runways = downloadLines(URL_RUNWAYS) { parseRunwaysCsv(it) }
            replaceCatalog(
                airports = { airports },
                runways = { runways },
                source = CatalogSource.DOWNLOADED,
                onProgress = onProgress
            )
        }

    private fun <T> downloadLines(url: String, parse: (Sequence<String>) -> T): T {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            requestMethod = "GET"
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("HTTP ${connection.responseCode} bij $url")
            }
            return connection.inputStream.bufferedReader().useLines(parse)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Swaps in a new catalogue. The clear-and-insert runs inside one transaction so the app can
     * never observe a half-populated catalogue, and the meta row is written last so its counts
     * always describe what is actually stored.
     */
    private suspend fun replaceCatalog(
        airports: () -> List<ParsedAirport>,
        runways: () -> List<ParsedRunway>,
        source: CatalogSource,
        onProgress: (CatalogProgress) -> Unit
    ) {
        val parsedAirports = airports()
        onProgress(CatalogProgress(parsedAirports.size, 0))
        val parsedRunways = runways()
        onProgress(CatalogProgress(parsedAirports.size, parsedRunways.size))

        // One transaction: the old catalogue stays queryable until this whole block commits, so
        // a failure part-way through rolls back rather than leaving a half-empty catalogue.
        transaction {
            airportDao.clear()
            runwayDao.clear()
            parsedAirports.chunked(INSERT_CHUNK).forEach { chunk ->
                airportDao.insertAll(chunk.map { it.toEntity() })
            }
            parsedRunways.chunked(INSERT_CHUNK).forEach { chunk ->
                runwayDao.insertAll(chunk.map { it.toEntity() })
            }
            metaDao.upsert(
                CatalogMetaEntity(
                    source = source.name,
                    loadedAtEpochMs = System.currentTimeMillis(),
                    airportCount = parsedAirports.size,
                    runwayCount = parsedRunways.size
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
                val newElevation = entry.elevationM ?: airfield.elevationM
                if (entry.name == airfield.name && newElevation == airfield.elevationM) return@forEach
                userRepository.saveAirfield(
                    airfield.copy(name = entry.name, elevationM = newElevation)
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
    }
}
