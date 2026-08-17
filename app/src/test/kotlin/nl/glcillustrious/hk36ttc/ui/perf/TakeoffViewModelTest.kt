package nl.glcillustrious.hk36ttc.ui.perf

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.parsePerformanceNormalData
import nl.glcillustrious.hk36ttc.core.wb.FuelTankType
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileEntity
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FakeAircraftProfileDao
import nl.glcillustrious.hk36ttc.data.local.FakeAirfieldDao
import nl.glcillustrious.hk36ttc.data.local.FakeRunwayStripDao
import nl.glcillustrious.hk36ttc.data.local.FlightContextMode
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.fakeAircraftProfileRepository

/**
 * [normalJson] is a literal copy of the fixture in core's own
 * `PerformanceCalculatorTest.kt` (itself a literal copy of docs/data/performance_normal.json,
 * AFM 3.01.20-E Rev. 4) — duplicated rather than shared across modules, per docs/00-plan.md
 * §11 point 4, since `core`'s test sources aren't exposed to `app`'s test source set.
 *
 * The opt-in covers `Dispatchers.setMain`/`resetMain` below: swapping the Main dispatcher is
 * still flagged experimental in kotlinx-coroutines 1.11.0, but it is the standard — and only —
 * way to give a `viewModelScope` a controllable dispatcher in a JVM unit test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TakeoffViewModelTest {

    private val normalJson = """
        {
          "takeoff": {
            "grass_runway_penalty_min_pct": 20,
            "table": [
              { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 0,    "s1_m": 158, "s2_m": 244 },
              { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 400,  "s1_m": 172, "s2_m": 260 },
              { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 800,  "s1_m": 186, "s2_m": 277 },
              { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 1200, "s1_m": 202, "s2_m": 297 },
              { "headwind_kts": 0, "oat_c": 15, "pressure_alt_m": 0,    "s1_m": 182, "s2_m": 274 },
              { "headwind_kts": 0, "oat_c": 15, "pressure_alt_m": 400,  "s1_m": 197, "s2_m": 292 },
              { "headwind_kts": 0, "oat_c": 15, "pressure_alt_m": 800,  "s1_m": 214, "s2_m": 314 },
              { "headwind_kts": 0, "oat_c": 15, "pressure_alt_m": 1200, "s1_m": 231, "s2_m": 336 },
              { "headwind_kts": 0, "oat_c": 30, "pressure_alt_m": 0,    "s1_m": 208, "s2_m": 307 },
              { "headwind_kts": 0, "oat_c": 30, "pressure_alt_m": 400,  "s1_m": 225, "s2_m": 328 },
              { "headwind_kts": 0, "oat_c": 30, "pressure_alt_m": 800,  "s1_m": 251, "s2_m": 363 },
              { "headwind_kts": 0, "oat_c": 30, "pressure_alt_m": 1200, "s1_m": 282, "s2_m": 400 },
              { "headwind_kts": 5, "oat_c": 0,  "pressure_alt_m": 0,    "s1_m": 129, "s2_m": 206 },
              { "headwind_kts": 5, "oat_c": 0,  "pressure_alt_m": 400,  "s1_m": 141, "s2_m": 220 },
              { "headwind_kts": 5, "oat_c": 0,  "pressure_alt_m": 800,  "s1_m": 153, "s2_m": 235 },
              { "headwind_kts": 5, "oat_c": 0,  "pressure_alt_m": 1200, "s1_m": 167, "s2_m": 253 },
              { "headwind_kts": 5, "oat_c": 15, "pressure_alt_m": 0,    "s1_m": 149, "s2_m": 232 },
              { "headwind_kts": 5, "oat_c": 15, "pressure_alt_m": 400,  "s1_m": 162, "s2_m": 248 },
              { "headwind_kts": 5, "oat_c": 15, "pressure_alt_m": 800,  "s1_m": 177, "s2_m": 267 },
              { "headwind_kts": 5, "oat_c": 15, "pressure_alt_m": 1200, "s1_m": 192, "s2_m": 287 },
              { "headwind_kts": 5, "oat_c": 30, "pressure_alt_m": 0,    "s1_m": 171, "s2_m": 261 },
              { "headwind_kts": 5, "oat_c": 30, "pressure_alt_m": 400,  "s1_m": 186, "s2_m": 280 },
              { "headwind_kts": 5, "oat_c": 30, "pressure_alt_m": 800,  "s1_m": 209, "s2_m": 309 },
              { "headwind_kts": 5, "oat_c": 30, "pressure_alt_m": 1200, "s1_m": 236, "s2_m": 344 },
              { "headwind_kts": 10, "oat_c": 0,  "pressure_alt_m": 0,    "s1_m": 103, "s2_m": 171 },
              { "headwind_kts": 10, "oat_c": 0,  "pressure_alt_m": 400,  "s1_m": 112, "s2_m": 183 },
              { "headwind_kts": 10, "oat_c": 0,  "pressure_alt_m": 800,  "s1_m": 123, "s2_m": 197 },
              { "headwind_kts": 10, "oat_c": 0,  "pressure_alt_m": 1200, "s1_m": 135, "s2_m": 212 },
              { "headwind_kts": 10, "oat_c": 15, "pressure_alt_m": 0,    "s1_m": 119, "s2_m": 193 },
              { "headwind_kts": 10, "oat_c": 15, "pressure_alt_m": 400,  "s1_m": 130, "s2_m": 208 },
              { "headwind_kts": 10, "oat_c": 15, "pressure_alt_m": 800,  "s1_m": 143, "s2_m": 224 },
              { "headwind_kts": 10, "oat_c": 15, "pressure_alt_m": 1200, "s1_m": 157, "s2_m": 241 },
              { "headwind_kts": 10, "oat_c": 30, "pressure_alt_m": 0,    "s1_m": 137, "s2_m": 218 },
              { "headwind_kts": 10, "oat_c": 30, "pressure_alt_m": 400,  "s1_m": 150, "s2_m": 236 },
              { "headwind_kts": 10, "oat_c": 30, "pressure_alt_m": 800,  "s1_m": 170, "s2_m": 261 },
              { "headwind_kts": 10, "oat_c": 30, "pressure_alt_m": 1200, "s1_m": 193, "s2_m": 291 }
            ]
          },
          "landing": {
            "table": [
              { "oat_c": 0,  "pressure_alt_m": 0,    "l1_m": 185, "l2_m": 375 },
              { "oat_c": 0,  "pressure_alt_m": 400,  "l1_m": 194, "l2_m": 393 },
              { "oat_c": 0,  "pressure_alt_m": 800,  "l1_m": 203, "l2_m": 411 },
              { "oat_c": 0,  "pressure_alt_m": 1200, "l1_m": 213, "l2_m": 432 },
              { "oat_c": 15, "pressure_alt_m": 0,    "l1_m": 195, "l2_m": 395 },
              { "oat_c": 15, "pressure_alt_m": 400,  "l1_m": 205, "l2_m": 414 },
              { "oat_c": 15, "pressure_alt_m": 800,  "l1_m": 214, "l2_m": 434 },
              { "oat_c": 15, "pressure_alt_m": 1200, "l1_m": 225, "l2_m": 456 },
              { "oat_c": 30, "pressure_alt_m": 0,    "l1_m": 205, "l2_m": 415 },
              { "oat_c": 30, "pressure_alt_m": 400,  "l1_m": 216, "l2_m": 435 },
              { "oat_c": 30, "pressure_alt_m": 800,  "l1_m": 225, "l2_m": 457 },
              { "oat_c": 30, "pressure_alt_m": 1200, "l1_m": 237, "l2_m": 489 }
            ]
          },
          "climb": {
            "vy_kmh": 110,
            "max_rate_of_climb_ms": 5.4,
            "service_ceiling_m": 5000
          },
          "demonstrated_crosswind_kmh": 15
        }
    """.trimIndent()

    private val performanceNormal = parsePerformanceNormalData(normalJson)
    private val corrections = PerformanceCorrectionsData.DEFAULT

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saved input round-trips when the screen is reopened for the same registration`() = runTest {
        val profileDao = FakeAircraftProfileDao()
        profileDao.seed(
            AircraftProfileEntity(
                id = 1,
                registration = "PH-XYZ",
                emptyMassKg = 560.0,
                emptyMassCgPositionMm = 2350.0,
                mtowKg = 770.0,
                cgEnvelopeForwardLimitMm = 2300.0,
                cgEnvelopeAftLimitMm = 2450.0,
                fuelTankType = FuelTankType.STANDARD_55L
            )
        )
        val repository = fakeAircraftProfileRepository(profileDao = profileDao)

        val firstOpen = TakeoffViewModel(repository, profileId = 1, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()

        firstOpen.update {
            it.copy(
                oatC = 20,
                pressureAltM = 300,
                headwindKts = 8,
                surfaceType = TakeoffSurfaceType.NAT_GRAS,
                slopePct = 2,
                marginFactorPct = 150
            )
        }
        testScheduler.advanceUntilIdle()

        val reopened = TakeoffViewModel(repository, profileId = 1, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()

        val state = reopened.state.value
        assertEquals("PH-XYZ", state.registration)
        assertEquals(20, state.oatC)
        assertEquals(300, state.pressureAltM)
        assertEquals(8, state.headwindKts)
        assertEquals(TakeoffSurfaceType.NAT_GRAS, state.surfaceType)
        assertEquals(2, state.slopePct)
        assertEquals(150, state.marginFactorPct)
    }

    @Test
    fun `a second registration keeps its own defaults untouched by the first one's saved input`() = runTest {
        val profileDao = FakeAircraftProfileDao()
        profileDao.seed(
            AircraftProfileEntity(
                id = 1,
                registration = "PH-XYZ",
                emptyMassKg = 560.0,
                emptyMassCgPositionMm = 2350.0,
                mtowKg = 770.0,
                cgEnvelopeForwardLimitMm = 2300.0,
                cgEnvelopeAftLimitMm = 2450.0,
                fuelTankType = FuelTankType.STANDARD_55L
            )
        )
        profileDao.seed(
            AircraftProfileEntity(
                id = 2,
                registration = "PH-ABC",
                emptyMassKg = 560.0,
                emptyMassCgPositionMm = 2350.0,
                mtowKg = 770.0,
                cgEnvelopeForwardLimitMm = 2300.0,
                cgEnvelopeAftLimitMm = 2450.0,
                fuelTankType = FuelTankType.STANDARD_55L
            )
        )
        val repository = fakeAircraftProfileRepository(profileDao = profileDao)

        val firstRegistration = TakeoffViewModel(repository, profileId = 1, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()
        firstRegistration.update { it.copy(oatC = 35, surfaceType = TakeoffSurfaceType.ZACHTE_GROND) }
        testScheduler.advanceUntilIdle()

        val secondRegistration = TakeoffViewModel(repository, profileId = 2, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()

        val state = secondRegistration.state.value
        assertEquals("PH-ABC", state.registration)
        assertEquals(15, state.oatC)
        assertEquals(TakeoffSurfaceType.ASFALT, state.surfaceType)
    }

    /**
     * Regression test for a real crash: [RunwayDirectionOption.designator] holds the display
     * *label* (e.g. "02"), while [nl.glcillustrious.hk36ttc.core.metar.RunwayCandidate.designator]
     * holds the stable *id* — mixing the two up in `recalculate()`'s
     * `directions.first { it.designator == candidate.designator }` lookup meant it could never
     * match (a label never equals an id), throwing `NoSuchElementException` the moment an
     * airfield with a usable METAR was selected. Confirmed on-device: a saved airfield with a
     * runway crashed the Take-off screen as soon as its METAR made the weather derivable.
     */
    @Test
    fun `selecting an airfield with a runway and a usable METAR does not crash`() = runTest {
        val profileDao = FakeAircraftProfileDao()
        profileDao.seed(
            AircraftProfileEntity(
                id = 1,
                registration = "PH-XYZ",
                emptyMassKg = 560.0,
                emptyMassCgPositionMm = 2350.0,
                mtowKg = 770.0,
                cgEnvelopeForwardLimitMm = 2300.0,
                cgEnvelopeAftLimitMm = 2450.0,
                fuelTankType = FuelTankType.STANDARD_55L
            )
        )
        val airfieldDao = FakeAirfieldDao()
        airfieldDao.seed(
            AirfieldEntity(
                id = 1,
                name = "Vliegbasis Gilze-Rijen",
                icao = "EHGR",
                metarStationIcao = null,
                elevationM = 15.0,
                metarRaw = "EHGR 161350Z 24012G20KT 9999 SCT025 18/12 Q1013 NOSIG",
                metarEnteredAtEpochMs = null
            )
        )
        val runwayStripDao = FakeRunwayStripDao()
        runwayStripDao.seed(
            RunwayStripEntity(
                id = 1,
                airfieldId = 1,
                designatorA = "02",
                designatorB = "20",
                headingDegTrueA = 20.0,
                lengthM = 1730.0,
                surface = "ASPHALT",
                slopePctA = 0.0
            )
        )
        val repository = fakeAircraftProfileRepository(
            profileDao = profileDao, airfieldDao = airfieldDao, runwayStripDao = runwayStripDao
        )

        val viewModel = TakeoffViewModel(repository, profileId = 1, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()

        viewModel.setFlightContextMode(FlightContextMode.AIRFIELD)
        testScheduler.advanceUntilIdle()
        viewModel.selectAirfield(airfieldDao.getById(1)!!)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.weatherDerivable)
        assertTrue(state.runwayResults.isNotEmpty())
        assertNotNull(state.result)
    }

    /**
     * Regression test for a real bug: the airfield the pilot selected was stored as a
     * one-time snapshot in [TakeoffFormState], so editing its METAR afterwards (e.g. on the
     * Airfield screen) never showed up here unless the pilot re-picked it from the dropdown —
     * exactly the "it decodes on the airfield screen but not on Take-off" report. Fixed by
     * deriving `selectedAirfield` from a live observation of the airfields table instead of a
     * frozen copy.
     */
    @Test
    fun `editing the selected airfield's METAR after selection is picked up without re-selecting it`() = runTest {
        val profileDao = FakeAircraftProfileDao()
        profileDao.seed(
            AircraftProfileEntity(
                id = 1,
                registration = "PH-XYZ",
                emptyMassKg = 560.0,
                emptyMassCgPositionMm = 2350.0,
                mtowKg = 770.0,
                cgEnvelopeForwardLimitMm = 2300.0,
                cgEnvelopeAftLimitMm = 2450.0,
                fuelTankType = FuelTankType.STANDARD_55L
            )
        )
        val airfieldDao = FakeAirfieldDao()
        airfieldDao.seed(
            AirfieldEntity(
                id = 1,
                name = "Vliegbasis Gilze-Rijen",
                icao = "EHGR",
                metarStationIcao = null,
                elevationM = 15.0,
                metarRaw = null,
                metarEnteredAtEpochMs = null
            )
        )
        val repository = fakeAircraftProfileRepository(profileDao = profileDao, airfieldDao = airfieldDao)

        val viewModel = TakeoffViewModel(repository, profileId = 1, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()

        viewModel.setFlightContextMode(FlightContextMode.AIRFIELD)
        testScheduler.advanceUntilIdle()
        viewModel.selectAirfield(airfieldDao.getById(1)!!)
        testScheduler.advanceUntilIdle()

        assertEquals(null, viewModel.state.value.selectedAirfield?.metarRaw)

        // Simulate editing the METAR on the Airfield screen after it's already selected here.
        airfieldDao.update(
            airfieldDao.getById(1)!!.copy(metarRaw = "EHGR 161825Z AUTO 35006KT 320V060 9999 FEW310 21/14 Q1016 BLU")
        )
        testScheduler.advanceUntilIdle()

        assertEquals(
            "EHGR 161825Z AUTO 35006KT 320V060 9999 FEW310 21/14 Q1016 BLU",
            viewModel.state.value.selectedAirfield?.metarRaw
        )
    }

    /**
     * Regression test: a variable/light wind (`VRB01KT`) makes the headwind component
     * un-computable, but temperature and QNH parse from the very same METAR regardless of
     * the wind group — OAT/pressure-altitude must stay derived (and hidden from manual entry)
     * even while [TakeoffFormState.windDirectionUnknown] prompts the pilot to type the wind
     * by hand. Before this fix, all three fell back to manual together.
     */
    @Test
    fun `variable wind still derives OAT and pressure altitude, only the wind itself needs manual entry`() = runTest {
        val profileDao = FakeAircraftProfileDao()
        profileDao.seed(
            AircraftProfileEntity(
                id = 1,
                registration = "PH-XYZ",
                emptyMassKg = 560.0,
                emptyMassCgPositionMm = 2350.0,
                mtowKg = 770.0,
                cgEnvelopeForwardLimitMm = 2300.0,
                cgEnvelopeAftLimitMm = 2450.0,
                fuelTankType = FuelTankType.STANDARD_55L
            )
        )
        val airfieldDao = FakeAirfieldDao()
        airfieldDao.seed(
            AirfieldEntity(
                id = 1,
                name = "Vliegbasis Gilze-Rijen",
                icao = "EHGR",
                metarStationIcao = null,
                elevationM = 15.0,
                metarRaw = "EHGR 161825Z VRB01KT 9999 FEW310 21/14 Q1016 BLU",
                metarEnteredAtEpochMs = null
            )
        )
        val runwayStripDao = FakeRunwayStripDao()
        runwayStripDao.seed(
            RunwayStripEntity(
                id = 1,
                airfieldId = 1,
                designatorA = "02",
                designatorB = "20",
                headingDegTrueA = 20.0,
                lengthM = 1730.0,
                surface = "ASPHALT",
                slopePctA = 0.0
            )
        )
        val repository = fakeAircraftProfileRepository(
            profileDao = profileDao, airfieldDao = airfieldDao, runwayStripDao = runwayStripDao
        )

        val viewModel = TakeoffViewModel(repository, profileId = 1, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()

        viewModel.setFlightContextMode(FlightContextMode.AIRFIELD)
        testScheduler.advanceUntilIdle()
        viewModel.selectAirfield(airfieldDao.getById(1)!!)
        testScheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.weatherDerivable)
        assertTrue(state.pressureAltDerivable)
        assertEquals(false, state.metarWindUsable)
        assertTrue(state.windDirectionUnknown)
        assertEquals(21, state.effectiveOatC)
        // 15m elevation + (1013.25 - 1016) * 8.23 m/hPa rounds to -8 m, but the AFM tables
        // start at 0 m, so the calculation floors it there rather than extrapolating below
        // sea level and flagging out-of-range for the most favourable case there is.
        assertEquals(0, state.effectivePressureAltM)
    }

    /**
     * Regression test for a real bug: 0°/0kt (the manual wind fields' default) is a real,
     * valid calm wind, so the per-runway results silently computed from that untouched
     * default the instant a variable-wind METAR made wind un-derivable — the pilot never saw
     * the "type the wind" prompt at all. Results must stay empty until the pilot actually
     * touches the wind fields ([TakeoffViewModel.update] with `windManuallySet = true`,
     * exactly what the Take-off screen's steppers do on every change).
     */
    @Test
    fun `results stay empty until the pilot actually enters a wind, even though 0 is a valid value`() = runTest {
        val profileDao = FakeAircraftProfileDao()
        profileDao.seed(
            AircraftProfileEntity(
                id = 1,
                registration = "PH-XYZ",
                emptyMassKg = 560.0,
                emptyMassCgPositionMm = 2350.0,
                mtowKg = 770.0,
                cgEnvelopeForwardLimitMm = 2300.0,
                cgEnvelopeAftLimitMm = 2450.0,
                fuelTankType = FuelTankType.STANDARD_55L
            )
        )
        val airfieldDao = FakeAirfieldDao()
        airfieldDao.seed(
            AirfieldEntity(
                id = 1,
                name = "Vliegbasis Gilze-Rijen",
                icao = "EHGR",
                metarStationIcao = null,
                elevationM = 15.0,
                metarRaw = "EHGR 161825Z VRB01KT 9999 FEW310 21/14 Q1016 BLU",
                metarEnteredAtEpochMs = null
            )
        )
        val runwayStripDao = FakeRunwayStripDao()
        runwayStripDao.seed(
            RunwayStripEntity(
                id = 1,
                airfieldId = 1,
                designatorA = "02",
                designatorB = "20",
                headingDegTrueA = 20.0,
                lengthM = 1730.0,
                surface = "ASPHALT",
                slopePctA = 0.0
            )
        )
        val repository = fakeAircraftProfileRepository(
            profileDao = profileDao, airfieldDao = airfieldDao, runwayStripDao = runwayStripDao
        )

        val viewModel = TakeoffViewModel(repository, profileId = 1, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()
        viewModel.setFlightContextMode(FlightContextMode.AIRFIELD)
        testScheduler.advanceUntilIdle()
        viewModel.selectAirfield(airfieldDao.getById(1)!!)
        testScheduler.advanceUntilIdle()

        assertEquals(false, viewModel.state.value.showRunwayResults)
        assertTrue(viewModel.state.value.runwayResults.isEmpty())

        viewModel.update { it.copy(windDirectionDeg = 20, windSpeedKts = 10, windManuallySet = true) }
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.showRunwayResults)
        assertTrue(viewModel.state.value.runwayResults.isNotEmpty())

        // The wind fields are the *source* of those results, so needing them by hand must not
        // switch off just because results now exist — the screen keeps both on display. An
        // earlier version hid the whole manual-wind block behind `!showRunwayResults`, which
        // made the fields erase themselves the instant a wind was entered.
        assertTrue(viewModel.state.value.windNeedsManualEntry)
        assertTrue(viewModel.state.value.windDirectionUnknown)
    }

    /**
     * The variable-wind fallback is only reachable while the pilot can actually see the fields
     * that drive it, so every flag the Take-off screen keys that UI on has to survive a plain
     * airfield switch — the reported symptom was the warning plus both wind fields appearing
     * for a frame and then vanishing again.
     */
    @Test
    fun `switching from a usable-wind airfield to a variable-wind one lands on manual wind entry`() = runTest {
        val profileDao = FakeAircraftProfileDao()
        profileDao.seed(
            AircraftProfileEntity(
                id = 1,
                registration = "PH-XYZ",
                emptyMassKg = 560.0,
                emptyMassCgPositionMm = 2350.0,
                mtowKg = 770.0,
                cgEnvelopeForwardLimitMm = 2300.0,
                cgEnvelopeAftLimitMm = 2450.0,
                fuelTankType = FuelTankType.STANDARD_55L
            )
        )
        val airfieldDao = FakeAirfieldDao()
        airfieldDao.seed(
            AirfieldEntity(
                id = 1,
                name = "Test",
                icao = "EHTL",
                metarStationIcao = null,
                elevationM = 10.0,
                metarRaw = "EHTL 161350Z 24012KT 9999 SCT025 18/12 Q1013 NOSIG",
                metarEnteredAtEpochMs = null
            )
        )
        airfieldDao.seed(
            AirfieldEntity(
                id = 2,
                name = "Vliegbasis Gilze-Rijen",
                icao = "EHGR",
                metarStationIcao = null,
                elevationM = 15.0,
                metarRaw = "EHGR 161825Z VRB01KT 9999 FEW310 21/14 Q1016 BLU",
                metarEnteredAtEpochMs = null
            )
        )
        val runwayStripDao = FakeRunwayStripDao()
        runwayStripDao.seed(
            RunwayStripEntity(
                id = 1, airfieldId = 1, designatorA = "05", designatorB = "23",
                headingDegTrueA = 50.0, lengthM = 1200.0, surface = "ASPHALT", slopePctA = 0.0
            )
        )
        runwayStripDao.seed(
            RunwayStripEntity(
                id = 2, airfieldId = 2, designatorA = "02", designatorB = "20",
                headingDegTrueA = 20.0, lengthM = 1730.0, surface = "ASPHALT", slopePctA = 0.0
            )
        )
        val repository = fakeAircraftProfileRepository(
            profileDao = profileDao, airfieldDao = airfieldDao, runwayStripDao = runwayStripDao
        )

        val viewModel = TakeoffViewModel(repository, profileId = 1, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()
        viewModel.setFlightContextMode(FlightContextMode.AIRFIELD)
        testScheduler.advanceUntilIdle()

        viewModel.selectAirfield(airfieldDao.getById(1)!!)
        testScheduler.advanceUntilIdle()
        // The usable-wind field computes straight away, with nothing to type by hand.
        assertTrue(viewModel.state.value.showRunwayResults)
        assertEquals(false, viewModel.state.value.windNeedsManualEntry)

        viewModel.selectAirfield(airfieldDao.getById(2)!!)
        testScheduler.advanceUntilIdle()

        // ...and the variable-wind field must settle on the manual-wind state, not flip back.
        val state = viewModel.state.value
        assertEquals(2L, state.selectedAirfield?.id)
        assertEquals(1, state.runwayStrips.size)
        assertEquals(false, state.metarWindUsable)
        assertTrue(state.windNeedsManualEntry)
        assertTrue(state.windDirectionUnknown)
        assertEquals(false, state.showRunwayResults)
        // OAT/pressure altitude still come from that same METAR — only the wind is missing.
        assertTrue(state.weatherDerivable)
        assertTrue(state.pressureAltDerivable)
        assertEquals(21, state.effectiveOatC)
    }

    /**
     * Switching to Handmatig keeps the previously selected airfield's runways in state on
     * purpose (switching back must not lose them), so "this airfield has grass" stays true —
     * but the grass-condition selector only drives the per-runway calculation, and Handmatig
     * asks for the surface outright instead. Leaving it on screen gave the pilot a control
     * that changed nothing.
     */
    @Test
    fun `the grass-condition selector disappears in Handmatig, even with a grass airfield still selected`() = runTest {
        val profileDao = FakeAircraftProfileDao()
        profileDao.seed(
            AircraftProfileEntity(
                id = 1,
                registration = "PH-XYZ",
                emptyMassKg = 560.0,
                emptyMassCgPositionMm = 2350.0,
                mtowKg = 770.0,
                cgEnvelopeForwardLimitMm = 2300.0,
                cgEnvelopeAftLimitMm = 2450.0,
                fuelTankType = FuelTankType.STANDARD_55L
            )
        )
        val airfieldDao = FakeAirfieldDao()
        airfieldDao.seed(
            AirfieldEntity(
                id = 1,
                name = "Terlet",
                icao = "EHTL",
                metarStationIcao = null,
                elevationM = 50.0,
                metarRaw = "EHTL 161350Z 24012KT 9999 SCT025 18/12 Q1013 NOSIG",
                metarEnteredAtEpochMs = null
            )
        )
        val runwayStripDao = FakeRunwayStripDao()
        runwayStripDao.seed(
            RunwayStripEntity(
                id = 1, airfieldId = 1, designatorA = "07", designatorB = "25",
                headingDegTrueA = 70.0, lengthM = 900.0, surface = "GRASS", slopePctA = 0.0
            )
        )
        val repository = fakeAircraftProfileRepository(
            profileDao = profileDao, airfieldDao = airfieldDao, runwayStripDao = runwayStripDao
        )

        val viewModel = TakeoffViewModel(repository, profileId = 1, performanceNormal = performanceNormal, corrections = corrections)
        testScheduler.advanceUntilIdle()
        viewModel.setFlightContextMode(FlightContextMode.AIRFIELD)
        viewModel.selectAirfield(airfieldDao.getById(1)!!)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.showGrassCondition)

        viewModel.setFlightContextMode(FlightContextMode.MANUAL)
        testScheduler.advanceUntilIdle()

        // The runways stay loaded — only the selector is gone.
        assertTrue(viewModel.state.value.hasGrassRunway)
        assertEquals(false, viewModel.state.value.showGrassCondition)
    }
}
