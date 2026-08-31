package nl.schellenberg.hk36ttc.ui.reallife

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.schellenberg.hk36ttc.core.metar.MetarConfigData
import nl.schellenberg.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.schellenberg.hk36ttc.core.perf.parsePerformanceNormalData
import nl.schellenberg.hk36ttc.core.reallife.ReallifeDetectionConfigData
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.AirfieldEntity
import nl.schellenberg.hk36ttc.data.local.ConditionsSource
import nl.schellenberg.hk36ttc.data.local.FakeAirfieldDao
import nl.schellenberg.hk36ttc.data.local.RealLifeConfiguration
import nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity
import nl.schellenberg.hk36ttc.data.local.fakeAircraftProfileRepository
import nl.schellenberg.hk36ttc.data.metar.HistoricalMetarRepository
import nl.schellenberg.hk36ttc.data.metar.MetarRepository
import nl.schellenberg.hk36ttc.ui.perf.TakeoffSurfaceType

/** [normalJson] is the same baseline row (0kt/15C/0m -> s1=182, s2=274) used in
 * `PerformanceCalculatorTest` — reused here so the comparison-math assertion below checks
 * against a real AFM figure, not an arbitrary number. */
@OptIn(ExperimentalCoroutinesApi::class)
class RealLifeLogDetailViewModelTest {

    private val normalJson = """
        {
          "takeoff": {
            "grass_runway_penalty_min_pct": 20,
            "table": [
              { "headwind_kts": 0, "oat_c": 15, "pressure_alt_m": 0, "s1_m": 182, "s2_m": 274 }
            ]
          },
          "landing": { "table": [ { "oat_c": 15, "pressure_alt_m": 0, "l1_m": 100, "l2_m": 150 } ] },
          "climb": { "vy_kmh": 100, "max_rate_of_climb_ms": 2.0, "service_ceiling_m": 3000 },
          "demonstrated_crosswind_kmh": 30
        }
    """.trimIndent()

    private val performanceNormal = parsePerformanceNormalData(normalJson)
    private val performanceCorrections = PerformanceCorrectionsData.DEFAULT
    private val metarConfig = MetarConfigData.DEFAULT
    private val reallifeDetectionConfig = ReallifeDetectionConfigData.DEFAULT

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun log() = RealLifeLogEntity(
        id = 1, profileId = 1, configuration = RealLifeConfiguration.NORMAL.name, notes = "",
        startedAtEpochMs = 1_000L, stoppedAtEpochMs = 5_000L, stopReason = "MANUAL",
        barometerAvailable = true, gpsRequestedIntervalMs = 1_000L
    )

    private fun viewModel(
        repository: AircraftProfileRepository,
        metarRepository: MetarRepository = MetarRepository(repository, fetch = { error("not used") }),
        historicalMetarRepository: HistoricalMetarRepository = HistoricalMetarRepository(fetch = { error("not used") })
    ) = RealLifeLogDetailViewModel(
        repository, profileId = 1, logId = 1, performanceNormal, performanceCorrections,
        metarRepository, historicalMetarRepository, metarConfig, reallifeDetectionConfig
    )

    @Test
    fun `loadAll populates the log and airfield list`() = runTest {
        val airfieldDao = FakeAirfieldDao().apply { seed(AirfieldEntity(1, "Gilze-Rijen", "EHGR", metarStationIcao = null, elevationM = 15.0, metarRaw = null, metarEnteredAtEpochMs = null)) }
        val repository = fakeAircraftProfileRepository(airfieldDao = airfieldDao)
        repository.startRealLifeLog(log())

        val viewModel = viewModel(repository)
        testScheduler.advanceUntilIdle()

        assertNotNull(viewModel.state.value.log)
        assertEquals(1, viewModel.state.value.airfields.size)
    }

    @Test
    fun `saving manual conditions computes the handboek comparison against the real AFM grid`() = runTest {
        val repository = fakeAircraftProfileRepository()
        repository.startRealLifeLog(log())
        val viewModel = viewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.beginEditingConditions()
        viewModel.updateSurfaceType(TakeoffSurfaceType.ASFALT)
        viewModel.updateSlopePct("0")
        viewModel.updateOatC("15")
        viewModel.updatePressureAltM("0")
        viewModel.saveConditions()
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(ConditionsSource.MANUAL.name, state.log?.conditionsSource)
        assertNotNull(state.comparison)
        assertEquals(274.0, state.comparison.s2M, 0.001)
        assertTrue(!state.isEditingConditions)
    }

    @Test
    fun `blank slope leaves conditions incomplete instead of saving a false 0 percent`() = runTest {
        val repository = fakeAircraftProfileRepository()
        repository.startRealLifeLog(log())
        val viewModel = viewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.beginEditingConditions()
        viewModel.updateSurfaceType(TakeoffSurfaceType.ASFALT)
        viewModel.updateSlopePct("")
        viewModel.updateOatC("15")
        viewModel.updatePressureAltM("0")
        viewModel.saveConditions()
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(null, state.log?.slopePct)
        assertEquals(null, state.comparison)
    }

    @Test
    fun `saving wind without a detected heading excludes headwind from the comparison and flags it`() = runTest {
        val repository = fakeAircraftProfileRepository()
        repository.startRealLifeLog(log())
        val viewModel = viewModel(repository)
        testScheduler.advanceUntilIdle()

        viewModel.beginEditingConditions()
        viewModel.updateSurfaceType(TakeoffSurfaceType.ASFALT)
        viewModel.updateSlopePct("0")
        viewModel.updateOatC("15")
        viewModel.updatePressureAltM("0")
        viewModel.updateWindDirectionDeg("260")
        viewModel.updateWindSpeedKts("10")
        viewModel.saveConditions()
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        // No location samples were recorded for this log, so TakeoffDetector never produces a
        // rollHeadingDegTrue -- the real wind that was just saved must not be silently treated
        // as calm, and the screen must be told to warn about it.
        assertNotNull(state.comparison)
        assertEquals(274.0, state.comparison.s2M, 0.001)
        assertTrue(state.comparisonHeadwindExcluded)
    }

    @Test
    fun `fetchLive populates form fields from the current METAR and marks the source`() = runTest {
        val airfieldDao = FakeAirfieldDao().apply { seed(AirfieldEntity(1, "Gilze-Rijen", "EHGR", metarStationIcao = null, elevationM = 15.0, metarRaw = null, metarEnteredAtEpochMs = null)) }
        val repository = fakeAircraftProfileRepository(airfieldDao = airfieldDao)
        repository.startRealLifeLog(log())
        val response = "METAR EHGR 170655Z AUTO 26005KT 5000 15/14 Q1015 WHT"
        val metarRepository = MetarRepository(repository, fetch = { response })
        val viewModel = viewModel(repository, metarRepository = metarRepository)
        testScheduler.advanceUntilIdle()

        viewModel.beginEditingConditions()
        viewModel.selectAirfield(1)
        // .join(), not testScheduler.advanceUntilIdle(): MetarRepository hops onto a real
        // Dispatchers.IO internally, outside the virtual test scheduler's control. join() is a
        // genuine suspend-until-complete that works regardless of which dispatcher the job
        // actually ran on.
        viewModel.fetchLive().join()

        val form = viewModel.state.value.form
        assertEquals(ConditionsSource.METAR_LIVE, form.source)
        assertEquals("15", form.oatC)
        assertEquals("260", form.windDirectionDeg)
        assertEquals("5", form.windSpeedKts)
        assertTrue(!form.fetchInProgress)
    }

    @Test
    fun `fetchHistorical populates form fields and marks the source, using a manually typed station`() = runTest {
        val repository = fakeAircraftProfileRepository()
        repository.startRealLifeLog(log())
        val csv = """
            station,valid,metar
            EHRD,1970-01-01 00:16,EHRD 010016Z 25012KT 9999 18/12 Q1017 NOSIG
        """.trimIndent()
        val historicalMetarRepository = HistoricalMetarRepository(fetch = { csv })
        val viewModel = viewModel(repository, historicalMetarRepository = historicalMetarRepository)
        testScheduler.advanceUntilIdle()

        viewModel.beginEditingConditions()
        viewModel.updateSourceMode(ConditionsSourceMode.HISTORICAL)
        viewModel.updateManualStation("EHRD")
        viewModel.fetchHistorical().join()

        val form = viewModel.state.value.form
        assertEquals(ConditionsSource.METAR_HISTORICAL, form.source)
        assertEquals("18", form.oatC)
        assertEquals("250", form.windDirectionDeg)
        assertTrue(!form.fetchInProgress)
        assertTrue(form.fetchError == null)
    }
}
