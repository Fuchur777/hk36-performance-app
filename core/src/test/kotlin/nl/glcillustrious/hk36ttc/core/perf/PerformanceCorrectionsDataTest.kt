package nl.glcillustrious.hk36ttc.core.perf

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [bundledJson] is a literal copy of app/src/main/assets/data/performance_corrections.json
 * (UK CAA AIC P 173/2024). core has no asset/file access of its own, so this is pinned here
 * as text — this test exists to catch drift between the two.
 */
class PerformanceCorrectionsDataTest {

    private val bundledJson = """
        {
          "source": {
            "document": "UK CAA AIC P 173/2024 - Take-off, Climb and Landing Performance of Light Aeroplanes",
            "related_source": "Same source family as CAP 1535 'SkyWay Code'",
            "note": "irrelevant metadata for parsing"
          },
          "grass_landing_factors": {
            "note": "irrelevant metadata for parsing",
            "dry_grass_up_to_20cm_factor": 1.15,
            "wet_grass_up_to_20cm_factor": 1.35,
            "very_short_smooth_grass_factor_upper_bound": 1.60,
            "very_short_smooth_grass_note": "irrelevant metadata for parsing"
          },
          "grass_takeoff_factors": {
            "note": "irrelevant metadata for parsing",
            "wet_grass_factor": 1.30,
            "soft_ground_factor": 1.25
          },
          "slope_correction": {
            "note": "irrelevant metadata for parsing",
            "factor_per_percent_slope": 0.05,
            "formula": "irrelevant metadata for parsing"
          },
          "margin_factor_defaults": {
            "note": "irrelevant metadata for parsing",
            "takeoff_default_factor": 1.33,
            "landing_default_factor": 1.43
          }
        }
    """.trimIndent()

    @Test
    fun `parsePerformanceCorrectionsData reads the real bundled JSON and matches the hardcoded DEFAULT fixture`() {
        assertEquals(PerformanceCorrectionsData.DEFAULT, parsePerformanceCorrectionsData(bundledJson))
    }

    @Test
    fun `slope factor is 1_0 (no-op) for a flat runway`() {
        val corrections = PerformanceCorrectionsData.DEFAULT
        assertEquals(1.0, corrections.slopeCorrection.factor(slopePct = 0.0, adverse = true))
    }

    @Test
    fun `slope factor is 1_0 for a favorable slope, even if nonzero`() {
        val corrections = PerformanceCorrectionsData.DEFAULT
        assertEquals(1.0, corrections.slopeCorrection.factor(slopePct = 2.0, adverse = false))
    }

    @Test
    fun `slope factor applies the AIC P173 formula for an adverse slope`() {
        val corrections = PerformanceCorrectionsData.DEFAULT
        // 2% uphill takeoff -> 1 + 0.05*2 = 1.10, matching the rekenlogica.md worked example.
        assertEquals(1.10, corrections.slopeCorrection.factor(slopePct = 2.0, adverse = true), absoluteTolerance = 1e-9)
    }

    private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
        kotlin.test.assertTrue(
            kotlin.math.abs(expected - actual) < absoluteTolerance,
            "expected=$expected actual=$actual"
        )
    }
}
