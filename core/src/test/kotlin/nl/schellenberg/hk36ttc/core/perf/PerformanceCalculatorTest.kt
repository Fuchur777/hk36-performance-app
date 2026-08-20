package nl.schellenberg.hk36ttc.core.perf

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [normalJson] is a literal copy of docs/data/performance_normal.json (AFM 3.01.20-E Rev. 4
 * §5.2.3/5.2.8/5.3.5). Expected values for grid points are read directly off that same
 * table (so they should match exactly, zero interpolation error); expected values for
 * mid-grid points were computed independently with awk before this test was written (see
 * session notes), not derived from the same interpolation code under test.
 */
class PerformanceCalculatorTest {

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

    private val data = parsePerformanceNormalData(normalJson)
    private val corrections = PerformanceCorrectionsData.DEFAULT

    private fun assertApprox(expected: Double, actual: Double, message: String, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) < tolerance, "$message: expected=$expected actual=$actual")
    }

    // --- Take-off: exact grid points ---

    @Test
    fun `T1 takeoff exact grid point matches the baseline row exactly`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 15.0, pressureAltM = 0.0, headwindKts = 0.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(182.0, result.s1M, "s1")
        assertApprox(274.0, result.s2M, "s2")
        assertApprox(182.0, result.s1WithMarginM, "s1 with margin=1.0")
        assertTrue(!result.outOfRangeWarning)
        assertTrue(!result.tailwindBlocked)
    }

    @Test
    fun `T2 takeoff exact grid point at max published wind-oat-altitude corner`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 30.0, pressureAltM = 1200.0, headwindKts = 10.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(193.0, result.s1M, "s1")
        assertApprox(291.0, result.s2M, "s2")
    }

    @Test
    fun `T3 takeoff exact grid point mid-table`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 0.0, pressureAltM = 800.0, headwindKts = 5.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(153.0, result.s1M, "s1")
        assertApprox(235.0, result.s2M, "s2")
    }

    @Test
    fun `T4 surface factor is a caller-supplied multiplier -- the AFM grass minimum is resolved by the caller, not this calculator`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 15.0, pressureAltM = 0.0, headwindKts = 0.0,
            surfaceFactor = 1.0 + data.takeoff.grassRunwayPenaltyMinPct / 100.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(182.0 * 1.20, result.s1M, "s1 with grass penalty")
        assertApprox(274.0 * 1.20, result.s2M, "s2 with grass penalty")
        assertTrue(result.surfaceFactorApplied)
    }

    @Test
    fun `T5 margin factor is a separate figure from the raw AFM number`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 15.0, pressureAltM = 0.0, headwindKts = 0.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.33
        )
        assertApprox(182.0, result.s1M, "raw AFM s1 unaffected by margin")
        assertApprox(182.0 * 1.33, result.s1WithMarginM, "s1 with margin")
        assertApprox(274.0 * 1.33, result.s2WithMarginM, "s2 with margin")
    }

    // --- Take-off: interpolation ---

    @Test
    fun `T6 takeoff bilinear interpolation at OAT-altitude midpoint`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 7.5, pressureAltM = 200.0, headwindKts = 0.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(177.25, result.s1M, "s1 midpoint")
        assertApprox(267.5, result.s2M, "s2 midpoint")
    }

    @Test
    fun `T7 takeoff wind interpolation happens after bilinear OAT-altitude per rekenlogica 2_1`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 15.0, pressureAltM = 0.0, headwindKts = 2.5,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(165.5, result.s1M, "s1 wind midpoint")
        assertApprox(253.0, result.s2M, "s2 wind midpoint")
    }

    @Test
    fun `T8 out-of-range OAT clamps to the published edge and flags a warning`() {
        val clamped = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 50.0, pressureAltM = 0.0, headwindKts = 0.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        val atEdge = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 30.0, pressureAltM = 0.0, headwindKts = 0.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(atEdge.s1M, clamped.s1M, "clamps to the 30C edge, never extrapolates")
        assertTrue(clamped.outOfRangeWarning)
        assertTrue(!atEdge.outOfRangeWarning)
    }

    @Test
    fun `T9 tailwind is blocked outright, not clamped to zero`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 15.0, pressureAltM = 0.0, headwindKts = -5.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(result.tailwindBlocked)
    }

    // --- Take-off: slope (AIC P173 §5.5) ---

    @Test
    fun `T14 uphill slope adds the AIC P173 correction to takeoff distance`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 15.0, pressureAltM = 0.0, headwindKts = 0.0,
            surfaceFactor = 1.0, slopePct = 2.0, marginFactor = 1.0
        )
        assertTrue(result.slopeApplied)
        assertApprox(182.0 * 1.10, result.s1M, "s1 with 2% uphill slope")
        assertApprox(274.0 * 1.10, result.s2M, "s2 with 2% uphill slope")
    }

    @Test
    fun `T15 downhill slope gives no takeoff correction -- source doesn't quantify a benefit`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 15.0, pressureAltM = 0.0, headwindKts = 0.0,
            surfaceFactor = 1.0, slopePct = -2.0, marginFactor = 1.0
        )
        assertTrue(!result.slopeApplied)
        assertApprox(182.0, result.s1M, "s1 unaffected by favorable slope")
        assertApprox(274.0, result.s2M, "s2 unaffected by favorable slope")
    }

    @Test
    fun `T19 takeoff wet-grass factor comes from AIC P173, not the AFM`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 15.0, pressureAltM = 0.0, headwindKts = 0.0,
            surfaceFactor = corrections.grassTakeoffFactors.wetGrassFactor, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(182.0 * 1.30, result.s1M, "s1 with wet-grass factor")
        assertApprox(274.0 * 1.30, result.s2M, "s2 with wet-grass factor")
    }

    @Test
    fun `T20 takeoff soft-ground factor comes from AIC P173, not the AFM`() {
        val result = PerformanceCalculator.calculateTakeoff(
            data, corrections, oatC = 15.0, pressureAltM = 0.0, headwindKts = 0.0,
            surfaceFactor = corrections.grassTakeoffFactors.softGroundFactor, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(182.0 * 1.25, result.s1M, "s1 with soft-ground factor")
        assertApprox(274.0 * 1.25, result.s2M, "s2 with soft-ground factor")
    }

    // --- Landing ---

    @Test
    fun `T10 landing exact grid point matches the baseline row exactly`() {
        val result = PerformanceCalculator.calculateLanding(
            data, corrections, oatC = 15.0, pressureAltM = 0.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(195.0, result.l1M, "l1")
        assertApprox(395.0, result.l2M, "l2")
    }

    @Test
    fun `T11 landing exact grid point at max published corner`() {
        val result = PerformanceCalculator.calculateLanding(
            data, corrections, oatC = 30.0, pressureAltM = 1200.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(237.0, result.l1M, "l1")
        assertApprox(489.0, result.l2M, "l2")
    }

    @Test
    fun `T12 landing bilinear interpolation at midpoint`() {
        val result = PerformanceCalculator.calculateLanding(
            data, corrections, oatC = 7.5, pressureAltM = 200.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.0
        )
        assertApprox(194.75, result.l1M, "l1 midpoint")
        assertApprox(394.25, result.l2M, "l2 midpoint")
    }

    @Test
    fun `T13 landing margin factor default from AIC P173`() {
        val result = PerformanceCalculator.calculateLanding(
            data, corrections, oatC = 15.0, pressureAltM = 0.0,
            surfaceFactor = 1.0, slopePct = 0.0, marginFactor = 1.43
        )
        assertApprox(195.0 * 1.43, result.l1WithMarginM, "l1 with margin")
        assertApprox(395.0 * 1.43, result.l2WithMarginM, "l2 with margin")
    }

    @Test
    fun `T16 landing grass surface factor is caller-supplied, Supplement 11 has none of its own`() {
        val result = PerformanceCalculator.calculateLanding(
            data, corrections, oatC = 15.0, pressureAltM = 0.0,
            surfaceFactor = corrections.grassLandingFactors.dryGrassFactor,
            slopePct = 0.0, marginFactor = 1.0
        )
        assertTrue(result.surfaceFactorApplied)
        assertApprox(195.0 * 1.15, result.l1M, "l1 with dry grass factor")
        assertApprox(395.0 * 1.15, result.l2M, "l2 with dry grass factor")
    }

    @Test
    fun `T17 downhill slope adds the AIC P173 correction to landing distance`() {
        val result = PerformanceCalculator.calculateLanding(
            data, corrections, oatC = 15.0, pressureAltM = 0.0,
            surfaceFactor = 1.0, slopePct = -2.0, marginFactor = 1.0
        )
        assertTrue(result.slopeApplied)
        assertApprox(195.0 * 1.10, result.l1M, "l1 with 2% downhill slope")
        assertApprox(395.0 * 1.10, result.l2M, "l2 with 2% downhill slope")
    }

    @Test
    fun `T18 uphill slope gives no landing correction -- source doesn't quantify a benefit`() {
        val result = PerformanceCalculator.calculateLanding(
            data, corrections, oatC = 15.0, pressureAltM = 0.0,
            surfaceFactor = 1.0, slopePct = 2.0, marginFactor = 1.0
        )
        assertTrue(!result.slopeApplied)
        assertApprox(195.0, result.l1M, "l1 unaffected by favorable slope")
        assertApprox(395.0, result.l2M, "l2 unaffected by favorable slope")
    }

    // --- Climb: static reference only, no calculation (rekenlogica.md §3) ---

    @Test
    fun `climb data is parsed as a static reference point, not interpolated`() {
        assertApprox(110.0, data.climb.vyKmh, "vy")
        assertApprox(5.4, data.climb.maxRateOfClimbMs, "max ROC")
        assertApprox(5000.0, data.climb.serviceCeilingM, "service ceiling")
    }
}
