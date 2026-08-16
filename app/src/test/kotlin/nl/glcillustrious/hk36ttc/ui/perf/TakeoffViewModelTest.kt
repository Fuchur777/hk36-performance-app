package nl.glcillustrious.hk36ttc.ui.perf

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.parsePerformanceNormalData
import nl.glcillustrious.hk36ttc.core.wb.FuelTankType
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileEntity
import nl.glcillustrious.hk36ttc.data.local.FakeAircraftProfileDao
import nl.glcillustrious.hk36ttc.data.local.fakeAircraftProfileRepository

/**
 * [normalJson] is a literal copy of the fixture in core's own
 * `PerformanceCalculatorTest.kt` (itself a literal copy of docs/data/performance_normal.json,
 * AFM 3.01.20-E Rev. 4) — duplicated rather than shared across modules, per docs/00-plan.md
 * §11 point 4, since `core`'s test sources aren't exposed to `app`'s test source set.
 */
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
}
