package nl.schellenberg.hk36ttc.core.perf

/**
 * Ground roll (s1/l1) and distance-over-15m-obstacle (s2/l2), before any margin factor is
 * applied. [marginFactor] is a user-adjustable recommended safety check, not an AFM figure
 * (rekenlogica.md §2.2) — the caller always supplies it explicitly, there is no hardcoded
 * default here, so a UI can source its own starting value however it likes without this
 * calculator silently assuming one.
 */
data class TakeoffResult(
    val s1M: Double,
    val s2M: Double,
    val s1WithMarginM: Double,
    val s2WithMarginM: Double,
    val surfaceFactorApplied: Boolean,
    val surfaceFactor: Double,
    val slopeApplied: Boolean,
    val slopePct: Double,
    val marginFactor: Double,
    val outOfRangeWarning: Boolean,
    val tailwindBlocked: Boolean
) {
    companion object {
        fun tailwindBlocked(): TakeoffResult = TakeoffResult(
            s1M = 0.0, s2M = 0.0, s1WithMarginM = 0.0, s2WithMarginM = 0.0,
            surfaceFactorApplied = false, surfaceFactor = 1.0,
            slopeApplied = false, slopePct = 0.0,
            marginFactor = 1.0, outOfRangeWarning = false, tailwindBlocked = true
        )
    }
}

data class LandingResult(
    val l1M: Double,
    val l2M: Double,
    val l1WithMarginM: Double,
    val l2WithMarginM: Double,
    val surfaceFactorApplied: Boolean,
    val surfaceFactor: Double,
    val slopeApplied: Boolean,
    val slopePct: Double,
    val marginFactor: Double,
    val outOfRangeWarning: Boolean
)

/**
 * Take-off/landing performance for the normal (non-tow) configuration. See rekenlogica.md
 * §2.1-2.2 for the interpolation method and post-corrections this implements, and §3 for why
 * climb has no calculation function at all (it's shown as a static reference point).
 */
object PerformanceCalculator {

    /**
     * [surfaceFactor] is a plain multiplier (1.0 = asphalt/no correction) — resolved by the
     * caller, since which figure applies (the AFM's own grass minimum, or an AIC P173 wet-
     * grass/soft-ground estimate) depends on which surface the user picked, not on anything
     * this calculator needs to know about. [slopePct] follows "positive = uphill in the
     * direction of travel" — only an uphill slope is adverse for take-off (AIC P173 §5.5); a
     * flat or downhill runway gets no correction at all, since the source doesn't quantify a
     * take-off benefit for that direction (rekenlogica.md §2.2).
     */
    fun calculateTakeoff(
        data: PerformanceNormalData,
        corrections: PerformanceCorrectionsData,
        oatC: Double,
        pressureAltM: Double,
        headwindKts: Double,
        surfaceFactor: Double,
        slopePct: Double,
        marginFactor: Double
    ): TakeoffResult {
        if (headwindKts < 0.0) return TakeoffResult.tailwindBlocked()

        val s1 = GridInterpolation.windBilinear(
            headwindKts, oatC, pressureAltM, data.takeoff.table,
            windOf = { it.headwindKts }, oatOf = { it.oatC }, altOf = { it.pressureAltM },
            valueOf = { it.s1M }
        )
        val s2 = GridInterpolation.windBilinear(
            headwindKts, oatC, pressureAltM, data.takeoff.table,
            windOf = { it.headwindKts }, oatOf = { it.oatC }, altOf = { it.pressureAltM },
            valueOf = { it.s2M }
        )

        val slopeAdverse = slopePct > 0.0
        val slopeMultiplier = corrections.slopeCorrection.factor(slopePct, adverse = slopeAdverse)
        val s1Corrected = s1.value * surfaceFactor * slopeMultiplier
        val s2Corrected = s2.value * surfaceFactor * slopeMultiplier

        return TakeoffResult(
            s1M = s1Corrected,
            s2M = s2Corrected,
            s1WithMarginM = s1Corrected * marginFactor,
            s2WithMarginM = s2Corrected * marginFactor,
            surfaceFactorApplied = surfaceFactor != 1.0,
            surfaceFactor = surfaceFactor,
            slopeApplied = slopeAdverse,
            slopePct = slopePct,
            marginFactor = marginFactor,
            outOfRangeWarning = s1.outOfRange || s2.outOfRange,
            tailwindBlocked = false
        )
    }

    /**
     * [surfaceFactor] is a plain multiplier (1.0 = asphalt/no correction) — resolved by the
     * caller from [PerformanceCorrectionsData.GrassLandingFactors] or a user-entered custom
     * value, since Supplement 11 publishes no grass-landing correction at all
     * (rekenlogica.md §2.2). [slopePct] follows "positive = uphill in the direction of
     * travel" — only a DOWNHILL slope is adverse for landing (AIC P173 §7.5); a flat or
     * uphill runway gets no correction.
     */
    fun calculateLanding(
        data: PerformanceNormalData,
        corrections: PerformanceCorrectionsData,
        oatC: Double,
        pressureAltM: Double,
        surfaceFactor: Double,
        slopePct: Double,
        marginFactor: Double
    ): LandingResult {
        val l1 = GridInterpolation.bilinear(
            oatC, pressureAltM, data.landing.table,
            oatOf = { it.oatC }, altOf = { it.pressureAltM }, valueOf = { it.l1M }
        )
        val l2 = GridInterpolation.bilinear(
            oatC, pressureAltM, data.landing.table,
            oatOf = { it.oatC }, altOf = { it.pressureAltM }, valueOf = { it.l2M }
        )

        val slopeAdverse = slopePct < 0.0
        val slopeMultiplier = corrections.slopeCorrection.factor(slopePct, adverse = slopeAdverse)
        val l1Corrected = l1.value * surfaceFactor * slopeMultiplier
        val l2Corrected = l2.value * surfaceFactor * slopeMultiplier

        return LandingResult(
            l1M = l1Corrected,
            l2M = l2Corrected,
            l1WithMarginM = l1Corrected * marginFactor,
            l2WithMarginM = l2Corrected * marginFactor,
            surfaceFactorApplied = surfaceFactor != 1.0,
            surfaceFactor = surfaceFactor,
            slopeApplied = slopeAdverse,
            slopePct = slopePct,
            marginFactor = marginFactor,
            outOfRangeWarning = l1.outOfRange || l2.outOfRange
        )
    }
}
