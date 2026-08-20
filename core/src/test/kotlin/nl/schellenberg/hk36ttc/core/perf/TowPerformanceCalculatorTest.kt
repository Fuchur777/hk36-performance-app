package nl.schellenberg.hk36ttc.core.perf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [towJson] is a literal excerpt of docs/data/performance_tow.json (Supplement No. 1,
 * 3.01.15-E Rev. 1) — only the classes actually exercised by these tests are included, but
 * every row present is copied verbatim from the source. Expected values at grid points are
 * read straight off that same table; interpolated/selection-logic expectations were computed
 * independently (awk / by hand) before writing the assertions.
 */
class TowPerformanceCalculatorTest {

    private val towJson = """
        {
          "limits": {
            "max_speed_kmh": 135,
            "min_speed_kmh": 97,
            "min_design_aerotow_speed_sailplane_kmh": 110,
            "max_towed_sailplane_mass_kg": 750,
            "max_towplane_takeoff_mass_solo_kg": 720,
            "max_towplane_takeoff_mass_dual_instruction_kg": 770,
            "max_towed_sailplane_mass_dual_instruction_kg": 380,
            "demonstrated_crosswind_kmh": 15
          },
          "takeoff_classes": {
            "classes": [
              {
                "id": "up_to_300kg",
                "sailplane_mass_max_kg": 300,
                "ld_ratio_min": 25,
                "towplane_mass_kg": 720,
                "table": [
                  { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 0,   "s1_m": 197, "s2_m": 363 },
                  { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 400, "s1_m": 219, "s2_m": 395 },
                  { "headwind_kts": 0, "oat_c": 15, "pressure_alt_m": 0,   "s1_m": 226, "s2_m": 410 },
                  { "headwind_kts": 0, "oat_c": 15, "pressure_alt_m": 400, "s1_m": 251, "s2_m": 448 }
                ]
              },
              {
                "id": "300_to_450kg",
                "sailplane_mass_min_kg": 300,
                "sailplane_mass_max_kg": 450,
                "ld_ratio_min": 38,
                "towplane_mass_kg": 720,
                "table": [
                  { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 0,   "s1_m": 240, "s2_m": 439 }
                ]
              },
              {
                "id": "450_to_600kg",
                "sailplane_mass_min_kg": 450,
                "sailplane_mass_max_kg": 600,
                "ld_ratio_min": 25,
                "towplane_mass_kg": 720,
                "table": [
                  { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 0,   "s1_m": 289, "s2_m": 525 }
                ]
              },
              {
                "id": "600_to_750kg",
                "sailplane_mass_min_kg": 600,
                "sailplane_mass_max_kg": 750,
                "ld_ratio_min": 58,
                "towplane_mass_kg": 720,
                "table": [
                  { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 0,   "s1_m": 291, "s2_m": 530 }
                ]
              },
              {
                "id": "instruction_flight",
                "description": "Dual flight, instruction purposes",
                "towplane_mass_kg": 770,
                "sailplane_mass_max_kg": 380,
                "ld_ratio_min": 38,
                "table": [
                  { "headwind_kts": 0, "oat_c": 0,  "pressure_alt_m": 0,   "s1_m": 224, "s2_m": 412 }
                ]
              }
            ]
          },
          "climb": {
            "points": [
              { "sailplane_mass_kg": 600, "max_rate_of_climb_ms": 2.3 },
              { "sailplane_mass_kg": 750, "max_rate_of_climb_ms": 2.1 }
            ]
          }
        }
    """.trimIndent()

    private val data = parsePerformanceTowData(towJson)
    private val corrections = PerformanceCorrectionsData.DEFAULT

    private fun assertApprox(expected: Double, actual: Double, message: String, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) < tolerance, "$message: expected=$expected actual=$actual")
    }

    @Test
    fun `T1 exact grid point, mass and L-D both satisfy the mass-appropriate class`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(!result.blocked)
        assertEquals("up_to_300kg", result.selectedClassId)
        assertTrue(!result.classBumpedUp)
        assertApprox(197.0, result.s1M, "s1")
        assertApprox(363.0, result.s2M, "s2")
    }

    @Test
    fun `surface correction factor defaults to a no-op (pure AFM grass figure)`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(!result.surfaceCorrectionApplied)
        assertApprox(197.0, result.s1M, "s1 unaffected by default correction")
    }

    @Test
    fun `surface correction factor is applied before the margin factor, not baked into the AFM figure`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.2, surfaceCorrectionFactor = 0.8
        )
        assertTrue(result.surfaceCorrectionApplied)
        assertApprox(197.0 * 0.8, result.s1M, "s1 with surface correction, no margin")
        assertApprox(363.0 * 0.8, result.s2M, "s2 with surface correction, no margin")
        assertApprox(197.0 * 0.8 * 1.2, result.s1WithMarginM, "s1 with both correction and margin")
        assertApprox(363.0 * 0.8 * 1.2, result.s2WithMarginM, "s2 with both correction and margin")
    }

    @Test
    fun `T2 insufficient L-D for the mass class bumps to the next heavier class`() {
        // 350kg falls in 300-450kg (requires L/D>=38), but L/D=30 doesn't meet it.
        // 450-600kg only requires L/D>=25, so 30 qualifies there.
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 350.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(!result.blocked)
        assertEquals("450_to_600kg", result.selectedClassId)
        assertTrue(result.classBumpedUp)
        assertApprox(289.0, result.s1M, "s1")
        assertApprox(525.0, result.s2M, "s2")
    }

    @Test
    fun `T3 no class satisfies the L-D requirement, even the heaviest -- blocked, not silently approximated`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 650.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(result.blocked)
        assertEquals(TowBlockReason.NoClassAvailable, result.blockReasons.single())
    }

    @Test
    fun `T4 sailplane mass over the hard limit blocks regardless of L-D`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 800.0, ldRatio = 60.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(result.blocked)
        assertTrue(result.blockReasons.any { it is TowBlockReason.SailplaneMassExceeded && it.maxKg == 750.0 })
    }

    @Test
    fun `T5 towplane solo takeoff mass over 720kg is blocked`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 725.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(result.blocked)
        assertTrue(result.blockReasons.any { it is TowBlockReason.TowplaneMassExceeded && it.maxKg == 720.0 })
    }

    @Test
    fun `T6 instruction flight uses the dedicated table and its own mass limits`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 300.0, ldRatio = null, instructionFlight = true,
            towplaneMassKg = 770.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(!result.blocked)
        assertEquals("instruction_flight", result.selectedClassId)
        assertApprox(224.0, result.s1M, "s1")
        assertApprox(412.0, result.s2M, "s2")
    }

    @Test
    fun `T7 instruction flight sailplane mass over 380kg is blocked`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 400.0, ldRatio = null, instructionFlight = true,
            towplaneMassKg = 770.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(result.blocked)
        assertTrue(result.blockReasons.any { it is TowBlockReason.SailplaneMassExceeded && it.maxKg == 380.0 })
    }

    @Test
    fun `T8 bilinear interpolation within a selected class`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 7.5, pressureAltM = 200.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(223.25, result.s1M, "s1 midpoint")
        assertApprox(404.0, result.s2M, "s2 midpoint")
    }

    @Test
    fun `T9 tailwind is blocked for tow takeoff too`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = -3.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(result.blocked)
    }

    @Test
    fun `T10 missing L-D skips the class-selection check and uses the mass-based class directly`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = null, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(!result.blocked)
        assertEquals("up_to_300kg", result.selectedClassId)
        assertTrue(!result.classBumpedUp)
    }

    @Test
    fun `T11 uphill slope adds the AIC P173 correction to tow takeoff distance`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 2.0, marginFactor = 1.0
        )
        assertTrue(result.slopeApplied)
        assertApprox(197.0 * 1.10, result.s1M, "s1 with 2% uphill slope")
        assertApprox(363.0 * 1.10, result.s2M, "s2 with 2% uphill slope")
    }

    @Test
    fun `T12 downhill slope gives no tow takeoff correction, same convention as normal takeoff`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = -2.0, marginFactor = 1.0
        )
        assertTrue(!result.slopeApplied)
        assertApprox(197.0, result.s1M, "s1 unaffected by favorable slope")
        assertApprox(363.0, result.s2M, "s2 unaffected by favorable slope")
    }

    @Test
    fun `T13 slope and surface correction combine multiplicatively before the margin factor`() {
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            data, corrections, sailplaneMassKg = 250.0, ldRatio = 30.0, instructionFlight = false,
            towplaneMassKg = 720.0, oatC = 0.0, pressureAltM = 0.0, headwindKts = 0.0,
            slopePct = 2.0, marginFactor = 1.2, surfaceCorrectionFactor = 0.8
        )
        assertApprox(197.0 * 0.8 * 1.10, result.s1M, "s1 with surface + slope, no margin")
        assertApprox(197.0 * 0.8 * 1.10 * 1.2, result.s1WithMarginM, "s1 with surface + slope + margin")
    }
}
