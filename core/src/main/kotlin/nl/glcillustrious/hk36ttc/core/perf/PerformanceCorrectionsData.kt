package nl.glcillustrious.hk36ttc.core.perf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Non-AFM correction factors from UK CAA AIC P 173/2024 ("Take-off, Climb and Landing
 * Performance of Light Aeroplanes"), loaded at runtime from `performance_corrections.json`
 * — same "never hardcode calculation values in Kotlin" rule as every other dataset in
 * `core`. Used only where the AFM/supplements themselves specify nothing (their baseline is
 * always "level, paved runway"). See rekenlogica.md §2.1-2.2b for the full source discussion
 * and the [AFM]/[AIC P173]/[APP] labeling convention every result derived from this file
 * must follow in the UI — never present these as AFM figures.
 */
@Serializable
data class PerformanceCorrectionsData(
    @SerialName("grass_landing_factors") val grassLandingFactors: GrassLandingFactors,
    @SerialName("grass_takeoff_factors") val grassTakeoffFactors: GrassTakeoffFactors,
    @SerialName("slope_correction") val slopeCorrection: SlopeCorrection,
    @SerialName("margin_factor_defaults") val marginFactorDefaults: MarginFactorDefaults
) {
    @Serializable
    data class GrassLandingFactors(
        @SerialName("dry_grass_up_to_20cm_factor") val dryGrassFactor: Double,
        @SerialName("wet_grass_up_to_20cm_factor") val wetGrassFactor: Double,
        @SerialName("very_short_smooth_grass_factor_upper_bound") val veryShortGrassFactorUpperBound: Double
    )

    /**
     * Take-off's dry-grass figure is NOT duplicated here — it's the same AFM minimum as
     * [nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData.Takeoff.grassRunwayPenaltyMinPct]
     * (AIC P173 §5 merely confirms it), so callers combine that AFM value with the wet/soft
     * figures here rather than risk the two files drifting apart.
     */
    @Serializable
    data class GrassTakeoffFactors(
        @SerialName("wet_grass_factor") val wetGrassFactor: Double,
        @SerialName("soft_ground_factor") val softGroundFactor: Double
    )

    @Serializable
    data class SlopeCorrection(
        @SerialName("factor_per_percent_slope") val factorPerPercentSlope: Double
    ) {
        /**
         * `slope_pct` is signed per the runway-direction convention in
         * airfield_profile_schema.json. [adverse] tells this function whether the caller has
         * already determined the slope is unfavorable for this operation (uphill for
         * take-off, downhill for landing) — a flat or favorable slope gets no correction at
         * all, since AIC P173 doesn't quantify a benefit for that direction.
         */
        fun factor(slopePct: Double, adverse: Boolean): Double =
            if (adverse && slopePct != 0.0) 1.0 + factorPerPercentSlope * kotlin.math.abs(slopePct) else 1.0
    }

    @Serializable
    data class MarginFactorDefaults(
        @SerialName("takeoff_default_factor") val takeoffDefaultFactor: Double,
        @SerialName("landing_default_factor") val landingDefaultFactor: Double
    )

    companion object {
        /**
         * Hardcoded copy of docs/data/performance_corrections.json (UK CAA AIC P 173/2024).
         * NOT used on the normal runtime path — see [nl.glcillustrious.hk36ttc.core.wb.WbConstantsData.DEFAULT]
         * for why this pattern exists (safety fallback + test fixture only).
         */
        val DEFAULT = PerformanceCorrectionsData(
            grassLandingFactors = GrassLandingFactors(
                dryGrassFactor = 1.15,
                wetGrassFactor = 1.35,
                veryShortGrassFactorUpperBound = 1.60
            ),
            grassTakeoffFactors = GrassTakeoffFactors(
                wetGrassFactor = 1.30,
                softGroundFactor = 1.25
            ),
            slopeCorrection = SlopeCorrection(factorPerPercentSlope = 0.05),
            marginFactorDefaults = MarginFactorDefaults(
                takeoffDefaultFactor = 1.33,
                landingDefaultFactor = 1.43
            )
        )
    }
}

private val performanceCorrectionsJson = Json { ignoreUnknownKeys = true }

fun parsePerformanceCorrectionsData(json: String): PerformanceCorrectionsData =
    performanceCorrectionsJson.decodeFromString(PerformanceCorrectionsData.serializer(), json)
