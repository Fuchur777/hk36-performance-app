package nl.glcillustrious.hk36ttc.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import nl.glcillustrious.hk36ttc.core.wb.FuelTankType
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileEntity
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FavoriteAirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FavoriteSailplaneTypeEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextEntity
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.TakeoffInputEntity
import nl.glcillustrious.hk36ttc.data.local.WbInputEntity

class UserDataRepositoryTest {

    private fun repository(
        dao: FakeUserDataDao,
        initialLanguage: String? = null
    ): Pair<UserDataRepository, () -> String?> {
        var language: String? = initialLanguage
        val repo = UserDataRepository(
            dao = dao,
            languageTag = { language },
            setLanguageTag = { language = it },
            transaction = { block -> block() },
            appVersionName = "0.8.0",
            now = { 1_700_000_000_000L }
        )
        return repo to { language }
    }

    /** A field with two strips, a favourite marker and saved calculation input — enough that
     * every kind of cross-table reference is exercised. */
    private fun seedRealisticData(dao: FakeUserDataDao) {
        dao.profiles += AircraftProfileEntity(
            id = 7, registration = "PH-1600", emptyMassKg = 560.0, emptyMassCgPositionMm = 400.0,
            mtowKg = 770.0, cgEnvelopeForwardLimitMm = 360.0, cgEnvelopeAftLimitMm = 420.0,
            fuelTankType = FuelTankType.LONG_RANGE_79L
        )
        dao.favoriteSailplaneTypes += FavoriteSailplaneTypeEntity("ASK 21")
        dao.airfields += AirfieldEntity(
            id = 3, name = "Terlet", icao = "EHTL", metarStationIcao = "EHDL",
            elevationM = 84.0, metarRaw = "EHDL 170655Z 26005KT 15/14 Q1015", metarEnteredAtEpochMs = 999L
        )
        dao.runwayStrips += RunwayStripEntity(
            id = 11, airfieldId = 3, designatorA = "04C", designatorB = "22C",
            headingDegTrueA = 40.0, lengthM = 1250.0, surface = "GRASS", slopePctA = 0.0
        )
        dao.runwayStrips += RunwayStripEntity(
            id = 12, airfieldId = 3, designatorA = "12", designatorB = "30",
            headingDegTrueA = 119.0, lengthM = 1097.0, surface = "GRASS", slopePctA = 1.0, oneWay = true
        )
        dao.favoriteAirfields += FavoriteAirfieldEntity(airfieldId = 3)
        dao.flightContexts += FlightContextEntity(profileId = 7, mode = "AIRFIELD", airfieldId = 3, grassCondition = "WET")
        dao.wbInputs += WbInputEntity(profileId = 7, pilotKg = 85, copilotKg = 0, fuelLiters = 40, baggageKg = 5)
        dao.takeoffInputs += TakeoffInputEntity(
            profileId = 7, oatC = 21, pressureAltM = 0, headwindKts = 6,
            surfaceType = "DROOG_GRAS", slopePct = 0, marginFactorPct = 133
        )
    }

    /**
     * The heart of the feature: export, write to JSON, read back into an empty database, and
     * every cross-table reference must still resolve. That works because import replaces rather
     * than merges, so rows go back under their original ids and nothing needs remapping.
     */
    @Test
    fun `a full round-trip through JSON restores every row and every reference`() = runTest {
        val source = FakeUserDataDao()
        seedRealisticData(source)
        val (exportRepo, _) = repository(source, initialLanguage = "nl")

        val json = exportRepo.serialize(exportRepo.buildExport())

        val target = FakeUserDataDao()
        val (importRepo, targetLanguage) = repository(target, initialLanguage = null)
        val parsed = importRepo.parse(json)
        assertTrue(parsed is ImportParseResult.Ok)
        importRepo.replaceAll(parsed.data)

        assertEquals(source.profiles, target.profiles)
        assertEquals(source.airfields, target.airfields)
        assertEquals(source.runwayStrips, target.runwayStrips)
        assertEquals(source.favoriteAirfields, target.favoriteAirfields)
        assertEquals(source.favoriteSailplaneTypes, target.favoriteSailplaneTypes)
        assertEquals(source.flightContexts, target.flightContexts)
        assertEquals(source.wbInputs, target.wbInputs)
        assertEquals(source.takeoffInputs, target.takeoffInputs)
        assertEquals("nl", targetLanguage())
    }

    /** Stated separately from the round-trip because this is the specific thing an id remap
     * would break: strips must still point at the airfield they belong to. */
    @Test
    fun `runway strips stay attached to their own airfield after an import`() = runTest {
        val source = FakeUserDataDao()
        seedRealisticData(source)
        val (exportRepo, _) = repository(source)
        val json = exportRepo.serialize(exportRepo.buildExport())

        val target = FakeUserDataDao()
        val (importRepo, _) = repository(target)
        importRepo.replaceAll((importRepo.parse(json) as ImportParseResult.Ok).data)

        val airfieldId = target.airfields.single().id
        assertEquals(2, target.runwayStrips.size)
        assertTrue(target.runwayStrips.all { it.airfieldId == airfieldId })
        assertEquals(airfieldId, target.favoriteAirfields.single().airfieldId)
        assertEquals(airfieldId, target.flightContexts.single().airfieldId)
        assertEquals(target.profiles.single().id, target.flightContexts.single().profileId)
    }

    @Test
    fun `importing replaces what was already there rather than adding to it`() = runTest {
        val source = FakeUserDataDao()
        seedRealisticData(source)
        val (exportRepo, _) = repository(source)
        val json = exportRepo.serialize(exportRepo.buildExport())

        val target = FakeUserDataDao()
        target.profiles += AircraftProfileEntity(
            id = 99, registration = "PH-OLD", emptyMassKg = 500.0, emptyMassCgPositionMm = 390.0,
            mtowKg = 750.0, cgEnvelopeForwardLimitMm = 350.0, cgEnvelopeAftLimitMm = 410.0,
            fuelTankType = FuelTankType.STANDARD_55L
        )
        target.airfields += AirfieldEntity(
            id = 99, name = "Oud veld", icao = null, metarStationIcao = null,
            elevationM = 0.0, metarRaw = null, metarEnteredAtEpochMs = null
        )
        val (importRepo, _) = repository(target)

        importRepo.replaceAll((importRepo.parse(json) as ImportParseResult.Ok).data)

        assertEquals(listOf("PH-1600"), target.profiles.map { it.registration })
        assertEquals(listOf("Terlet"), target.airfields.map { it.name })
    }

    @Test
    fun `a file from a newer app version is refused without touching the database`() = runTest {
        val dao = FakeUserDataDao()
        seedRealisticData(dao)
        val (repo, _) = repository(dao)
        val futureJson = """
            {
              "schema_version": ${UserDataExport.CURRENT_SCHEMA_VERSION + 1},
              "exported_at_epoch_ms": 1,
              "app_version_name": "9.9.9"
            }
        """.trimIndent()

        val result = repo.parse(futureJson)

        assertTrue(result is ImportParseResult.TooNew)
        assertEquals(UserDataExport.CURRENT_SCHEMA_VERSION + 1, result.fileVersion)
        // parse() must never write; the pilot still has everything they had.
        assertEquals(1, dao.profiles.size)
        assertEquals(2, dao.runwayStrips.size)
    }

    @Test
    fun `a corrupt or unrelated file is refused without touching the database`() = runTest {
        val dao = FakeUserDataDao()
        seedRealisticData(dao)
        val (repo, _) = repository(dao)

        assertTrue(repo.parse("this is not json at all") is ImportParseResult.Unreadable)
        assertTrue(repo.parse("") is ImportParseResult.Unreadable)
        assertEquals(1, dao.profiles.size)
    }

    /** An older/smaller file that simply omits sections must import as "empty", not fail — every
     * list has a default. */
    @Test
    fun `a minimal file with only the required header imports as empty`() = runTest {
        val dao = FakeUserDataDao()
        seedRealisticData(dao)
        val (repo, _) = repository(dao)
        val minimal = """
            {"schema_version":1,"exported_at_epoch_ms":1,"app_version_name":"0.8.0"}
        """.trimIndent()

        val parsed = repo.parse(minimal)
        assertTrue(parsed is ImportParseResult.Ok)
        repo.replaceAll(parsed.data)

        assertTrue(dao.profiles.isEmpty())
        assertTrue(dao.airfields.isEmpty())
        assertTrue(dao.runwayStrips.isEmpty())
    }

    @Test
    fun `the language override travels with the backup`() = runTest {
        val source = FakeUserDataDao()
        val (exportRepo, _) = repository(source, initialLanguage = "en")
        val json = exportRepo.serialize(exportRepo.buildExport())

        val target = FakeUserDataDao()
        val (importRepo, targetLanguage) = repository(target, initialLanguage = "nl")
        val parsed = importRepo.parse(json) as ImportParseResult.Ok

        assertTrue(importRepo.languageWouldChange(parsed.data))
        importRepo.replaceAll(parsed.data)
        assertEquals("en", targetLanguage())
    }

    @Test
    fun `following the device language is exported as no override, not as a guess`() = runTest {
        val dao = FakeUserDataDao()
        val (repo, _) = repository(dao, initialLanguage = null)

        assertNull(repo.buildExport().languageTag)
    }

    @Test
    fun `the export carries counts the confirmation dialog can show`() = runTest {
        val dao = FakeUserDataDao()
        seedRealisticData(dao)
        val (repo, _) = repository(dao)

        val export = repo.buildExport()

        assertEquals(1, export.profileCount)
        assertEquals(1, export.airfieldCount)
        assertEquals(UserDataExport.CURRENT_SCHEMA_VERSION, export.schemaVersion)
        assertEquals("0.8.0", export.appVersionName)
    }

    /**
     * Several columns hold an enum's `name` and are read back with `valueOf`, which throws on
     * anything unexpected. Since importing wipes every table before it inserts, a bad value
     * caught only at read time would cost the pilot their data *and* leave a screen that crashes
     * on open — so the file has to be refused before the wipe, while refusing still costs
     * nothing.
     */
    @Test
    fun `a backup naming an unknown enum value is refused without touching the database`() = runTest {
        val dao = FakeUserDataDao()
        val (repo, _) = repository(dao)
        seedRealisticData(dao)
        val before = repo.buildExport()

        val json = repo.serialize(before).replace("\"GRASS\"", "\"GRAVEL\"")
        val result = repo.parse(json)

        assertTrue(result is ImportParseResult.Unreadable, "expected Unreadable, got $result")
        assertTrue(result.reason.contains("GRAVEL"), "the message should name the value: ${result.reason}")
        // Nothing was written: parse() must never reach the database.
        assertEquals(before.airfields.size, dao.airfields.size)
        assertEquals(before.runwayStrips.size, dao.runwayStrips.size)
    }

    @Test
    fun `a backup whose every enum value is known still parses`() = runTest {
        val dao = FakeUserDataDao()
        val (repo, _) = repository(dao)
        seedRealisticData(dao)

        val result = repo.parse(repo.serialize(repo.buildExport()))

        assertTrue(result is ImportParseResult.Ok, "expected Ok, got $result")
    }
}
