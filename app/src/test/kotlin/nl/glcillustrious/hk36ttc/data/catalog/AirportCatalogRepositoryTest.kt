package nl.glcillustrious.hk36ttc.data.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FakeAirfieldDao
import nl.glcillustrious.hk36ttc.data.local.FakeRunwayStripDao
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.fakeAircraftProfileRepository

/**
 * The rule this whole feature is built around: imported data may add to the pilot's airfields,
 * but must never delete, replace or merge with runways they entered themselves.
 */
class AirportCatalogRepositoryTest {

    private val airportDao = FakeAirportCatalogDao()
    private val runwayDao = FakeRunwayCatalogDao()
    private val airfieldDao = FakeAirfieldDao()
    private val runwayStripDao = FakeRunwayStripDao()

    private val userRepository = fakeAircraftProfileRepository(
        airfieldDao = airfieldDao,
        runwayStripDao = runwayStripDao
    )

    private val repository = AirportCatalogRepository(
        airportDao = airportDao,
        runwayDao = runwayDao,
        metaDao = FakeCatalogMetaDao(),
        userRepository = userRepository,
        openAsset = { error("no assets in this test") },
        transaction = { block -> block() },
        newTempFile = { error("no downloads in this test") }
    )

    private fun seedCatalogGilzeRijen() {
        airportDao.seed(
            AirportCatalogEntity(
                ident = "EHGR",
                type = "medium_airport",
                name = "Gilze Rijen Air Base",
                elevationM = 14.9352,
                isoCountry = "NL",
                municipality = "Rijen",
                icaoCode = "EHGR",
                gpsCode = "EHGR",
                localCode = null,
                latitudeDeg = 51.5674,
                longitudeDeg = 4.93183
            )
        )
        runwayDao.seed(
            RunwayCatalogEntity(
                airportIdent = "EHGR", designatorA = "02", designatorB = "20",
                headingDegTrueA = 16.0, headingDerived = false, lengthM = 1996.4,
                surface = "ASPHALT", oneWay = false
            )
        )
        runwayDao.seed(
            RunwayCatalogEntity(
                airportIdent = "EHGR", designatorA = "10", designatorB = "28",
                headingDegTrueA = 98.0, headingDerived = false, lengthM = 2767.9,
                surface = "ASPHALT", oneWay = false
            )
        )
    }

    /**
     * Terlet exactly as the real source has it: **no `icao_code` at all**, with `EHTL` living in
     * `ident`/`gps_code` instead. That is the normal shape for a gliding site, and it is why
     * looking an airfield up by the ICAO column alone found nothing.
     */
    private fun seedCatalogTerlet() {
        airportDao.seed(
            AirportCatalogEntity(
                ident = "EHTL",
                type = "small_airport",
                name = "Terlet Glidersite",
                elevationM = 84.1248,
                isoCountry = "NL",
                municipality = "Arnhem",
                icaoCode = null,
                gpsCode = "EHTL",
                localCode = null,
                latitudeDeg = 52.0572,
                longitudeDeg = 5.92444
            )
        )
        runwayDao.seed(
            RunwayCatalogEntity(
                airportIdent = "EHTL", designatorA = "04C", designatorB = "22C",
                headingDegTrueA = 40.0, headingDerived = false, lengthM = 1250.0,
                surface = "GRASS", oneWay = false
            )
        )
        runwayDao.seed(
            RunwayCatalogEntity(
                airportIdent = "EHTL", designatorA = "12", designatorB = "30",
                headingDegTrueA = 119.0, headingDerived = false, lengthM = 1097.3,
                surface = "GRASS", oneWay = false
            )
        )
    }

    private fun seedUserAirfield(icao: String?, name: String = "Mijn veld", elevationM: Double = 0.0): Long {
        airfieldDao.seed(
            AirfieldEntity(
                id = 1, name = name, icao = icao, metarStationIcao = "EHDL",
                elevationM = elevationM, metarRaw = "EHGR 161825Z VRB01KT 9999 21/14 Q1016",
                metarEnteredAtEpochMs = 123L
            )
        )
        return 1L
    }

    // --- The hard rule -----------------------------------------------------------------

    @Test
    fun `an airfield that already has its own runways is refused outright`() = runTest {
        seedCatalogGilzeRijen()
        val airfieldId = seedUserAirfield("EHGR")
        val mine = RunwayStripEntity(
            id = 1, airfieldId = airfieldId, designatorA = "02 gras", designatorB = "20 gras",
            headingDegTrueA = 20.0, lengthM = 800.0, surface = "GRASS", slopePctA = 1.5
        )
        runwayStripDao.seed(mine)

        val result = repository.importRunwaysForAirfield(airfieldId, "EHGR")

        assertEquals(RunwayImportResult.AlreadyHasRunways, result)
        // Untouched: same single strip, same values, nothing appended alongside it.
        assertEquals(listOf(mine), runwayStripDao.getByAirfield(airfieldId))
    }

    @Test
    fun `an airfield with no runways of its own gets them from the catalogue`() = runTest {
        seedCatalogGilzeRijen()
        val airfieldId = seedUserAirfield("EHGR")

        val result = repository.importRunwaysForAirfield(airfieldId, "EHGR")

        assertEquals(RunwayImportResult.Imported(imported = 2, withDerivedHeading = 0), result)
        val strips = runwayStripDao.getByAirfield(airfieldId)
        assertEquals(2, strips.size)
        val longest = strips.maxBy { it.lengthM }
        assertEquals("10", longest.designatorA)
        assertEquals("28", longest.designatorB)
        assertEquals(98.0, longest.headingDegTrueA)
        assertEquals("ASPHALT", longest.surface)
        // No public dataset publishes slope, so it must stay at the neutral default.
        assertEquals(0.0, longest.slopePctA)
    }

    @Test
    fun `derived headings are counted so the screen can tell the pilot to check them`() = runTest {
        airportDao.seed(
            AirportCatalogEntity(
                ident = "XXXX", type = "small_airport", name = "Ergens", elevationM = 10.0,
                isoCountry = "NL", municipality = null, icaoCode = "XXXX", gpsCode = null,
                localCode = null, latitudeDeg = null, longitudeDeg = null
            )
        )
        runwayDao.seed(
            RunwayCatalogEntity(
                airportIdent = "XXXX", designatorA = "07", designatorB = "25",
                headingDegTrueA = 70.0, headingDerived = true, lengthM = 900.0,
                surface = "GRASS", oneWay = false
            )
        )
        runwayDao.seed(
            RunwayCatalogEntity(
                airportIdent = "XXXX", designatorA = "12", designatorB = null,
                headingDegTrueA = 119.0, headingDerived = false, lengthM = 700.0,
                surface = "GRASS", oneWay = true
            )
        )
        val airfieldId = seedUserAirfield("XXXX")

        val result = repository.importRunwaysForAirfield(airfieldId, "XXXX")

        assertEquals(RunwayImportResult.Imported(imported = 2, withDerivedHeading = 1), result)
        // A one-way strip keeps the app's own "designatorB is ignored" placeholder convention.
        val oneWay = runwayStripDao.getByAirfield(airfieldId).single { it.oneWay }
        assertEquals("", oneWay.designatorB)
    }

    @Test
    fun `an airfield the catalogue does not know reports nothing available and stays empty`() = runTest {
        seedCatalogGilzeRijen()
        val airfieldId = seedUserAirfield("EHXX")

        val result = repository.importRunwaysForAirfield(airfieldId, "EHXX")

        assertEquals(RunwayImportResult.NothingAvailable, result)
        assertTrue(runwayStripDao.getByAirfield(airfieldId).isEmpty())
    }

    // --- Updating existing airfields ---------------------------------------------------

    @Test
    fun `updating from the catalogue refreshes name and elevation but nothing else`() = runTest {
        seedCatalogGilzeRijen()
        seedUserAirfield("EHGR", name = "Gilze", elevationM = 40.0)
        val before = airfieldDao.getById(1)!!

        val result = repository.updateAirfieldsFromCatalog(listOf(before))

        assertEquals(AirfieldRefreshResult(updated = 1, checked = 1), result)
        val after = assertNotNull(airfieldDao.getById(1))
        assertEquals("Gilze Rijen Air Base", after.name)
        assertEquals(14.9352, after.elevationM)
        // Everything the pilot owns is left exactly as it was.
        assertEquals(before.metarStationIcao, after.metarStationIcao)
        assertEquals(before.metarRaw, after.metarRaw)
        assertEquals(before.metarEnteredAtEpochMs, after.metarEnteredAtEpochMs)
        assertEquals(before.icao, after.icao)
    }

    @Test
    fun `updating never touches runways, even for an airfield it does rename`() = runTest {
        seedCatalogGilzeRijen()
        val airfieldId = seedUserAirfield("EHGR", name = "Gilze", elevationM = 40.0)
        val mine = RunwayStripEntity(
            id = 1, airfieldId = airfieldId, designatorA = "02 gras", designatorB = "20 gras",
            headingDegTrueA = 20.0, lengthM = 800.0, surface = "GRASS", slopePctA = 1.5
        )
        runwayStripDao.seed(mine)

        repository.updateAirfieldsFromCatalog(listOf(airfieldDao.getById(airfieldId)!!))

        assertEquals("Gilze Rijen Air Base", airfieldDao.getById(airfieldId)!!.name)
        assertEquals(listOf(mine), runwayStripDao.getByAirfield(airfieldId))
    }

    @Test
    fun `an airfield without an ICAO code is skipped rather than guessed at`() = runTest {
        seedCatalogGilzeRijen()
        seedUserAirfield(icao = null, name = "Eigen weiland")

        val result = repository.updateAirfieldsFromCatalog(listOf(airfieldDao.getById(1)!!))

        assertEquals(AirfieldRefreshResult(updated = 0, checked = 0), result)
        assertEquals("Eigen weiland", airfieldDao.getById(1)!!.name)
    }

    @Test
    fun `an unknown elevation in the catalogue leaves the pilot's own value alone`() = runTest {
        airportDao.seed(
            AirportCatalogEntity(
                ident = "EHTL", type = "small_airport", name = "Terlet Glidersite", elevationM = null,
                isoCountry = "NL", municipality = "Arnhem", icaoCode = "EHTL", gpsCode = "EHTL",
                localCode = null, latitudeDeg = null, longitudeDeg = null
            )
        )
        seedUserAirfield("EHTL", name = "Terlet", elevationM = 84.0)

        repository.updateAirfieldsFromCatalog(listOf(airfieldDao.getById(1)!!))

        val after = airfieldDao.getById(1)!!
        assertEquals("Terlet Glidersite", after.name)
        assertEquals(84.0, after.elevationM)
    }

    // --- Creating from the catalogue ---------------------------------------------------

    @Test
    fun `creating an airfield from a catalogue entry carries name, code and elevation`() = runTest {
        seedCatalogGilzeRijen()
        val entry = airportDao.findByCode("EHGR")!!

        val id = repository.createAirfieldFrom(entry)

        val created = assertNotNull(airfieldDao.getById(id))
        assertEquals("Gilze Rijen Air Base", created.name)
        assertEquals("EHGR", created.icao)
        assertEquals(14.9352, created.elevationM)
        // A fresh airfield starts with no runways at all — they are pulled in as a separate,
        // explicit step.
        assertTrue(runwayStripDao.getByAirfield(id).isEmpty())
    }

    // --- Airfields without an official ICAO code ---------------------------------------

    /**
     * Regression: Terlet's runways were reported as "none available" even though the source has
     * six of them, because it has no `icao_code` and the lookup matched only that column. Most
     * gliding sites are shaped this way, so this is the common case rather than an edge one.
     */
    @Test
    fun `an airfield whose code is only a GPS code still finds its catalogue entry and runways`() = runTest {
        seedCatalogTerlet()
        val airfieldId = seedUserAirfield(icao = "EHTL", name = "Terlet")

        val ident = repository.catalogIdentFor(airfieldDao.getById(airfieldId)!!)
        assertEquals("EHTL", ident)

        val result = repository.importRunwaysForAirfield(airfieldId, ident!!)

        assertEquals(RunwayImportResult.Imported(imported = 2, withDerivedHeading = 0), result)
        assertEquals(2, runwayStripDao.getByAirfield(airfieldId).size)
    }

    @Test
    fun `name and elevation also refresh for an airfield that has no ICAO code`() = runTest {
        seedCatalogTerlet()
        val airfieldId = seedUserAirfield(icao = "EHTL", name = "Terlet oud", elevationM = 0.0)

        val result = repository.updateAirfieldsFromCatalog(listOf(airfieldDao.getById(airfieldId)!!))

        assertEquals(AirfieldRefreshResult(updated = 1, checked = 1), result)
        assertEquals("Terlet Glidersite", airfieldDao.getById(airfieldId)!!.name)
    }

    /** A real ICAO match must outrank a GPS/local code that happens to be the same string. */
    @Test
    fun `a genuine ICAO match wins over another field's matching local code`() = runTest {
        airportDao.seed(
            AirportCatalogEntity(
                ident = "XXXX", type = "small_airport", name = "Toevallige naamgenoot",
                elevationM = 0.0, isoCountry = "US", municipality = null,
                icaoCode = null, gpsCode = null, localCode = "EHGR",
                latitudeDeg = null, longitudeDeg = null
            )
        )
        seedCatalogGilzeRijen()

        assertEquals("Gilze Rijen Air Base", airportDao.findByCode("EHGR")?.name)
    }
}
