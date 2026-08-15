package nl.glcillustrious.hk36ttc.core.perf

/** Result of matching a sailplane weight/L-D combination to a take-off table class. */
sealed interface TowClassSelectionResult {
    data class Selected(val towClass: PerformanceTowData.TowClass, val bumpedUp: Boolean) : TowClassSelectionResult
    data object NoDataAvailable : TowClassSelectionResult
}

/**
 * Why a tow take-off can't be calculated — a typed reason rather than a literal message, since
 * `core` is a plain Kotlin/JVM module with no Android resources of its own to localize a
 * message string with. The app layer maps each case to a localized string (see
 * `SleepvluchtScreen.kt`'s `BlockedReasonsCard`).
 */
sealed interface TowBlockReason {
    data object TailwindNotSupported : TowBlockReason
    data class SailplaneMassExceeded(val actualKg: Double, val maxKg: Double, val instructionFlight: Boolean) : TowBlockReason
    data class TowplaneMassExceeded(val actualKg: Double, val maxKg: Double, val instructionFlight: Boolean) : TowBlockReason
    data object InstructionClassMissing : TowBlockReason
    data object NoClassAvailable : TowBlockReason
}

data class TowTakeoffResult(
    val s1M: Double,
    val s2M: Double,
    val s1WithMarginM: Double,
    val s2WithMarginM: Double,
    val marginFactor: Double,
    val surfaceCorrectionApplied: Boolean,
    val surfaceCorrectionFactor: Double,
    val slopeApplied: Boolean,
    val slopePct: Double,
    val outOfRangeWarning: Boolean,
    val blocked: Boolean,
    val blockReasons: List<TowBlockReason>,
    val selectedClassId: String?,
    val classBumpedUp: Boolean
) {
    companion object {
        fun blocked(reasons: List<TowBlockReason>): TowTakeoffResult = TowTakeoffResult(
            s1M = 0.0, s2M = 0.0, s1WithMarginM = 0.0, s2WithMarginM = 0.0, marginFactor = 1.0,
            surfaceCorrectionApplied = false, surfaceCorrectionFactor = 1.0,
            slopeApplied = false, slopePct = 0.0,
            outOfRangeWarning = false, blocked = true, blockReasons = reasons,
            selectedClassId = null, classBumpedUp = false
        )
    }
}

/**
 * Tow-plane (sailplane towing) take-off performance. See rekenlogica.md §2.3. Landing after
 * a tow release deliberately has no function here — the AFM has no separate tow-landing
 * data, so the normal (non-tow) [PerformanceCalculator.calculateLanding] applies once the
 * sailplane has been released.
 */
object TowPerformanceCalculator {

    /**
     * Picks the take-off table class for [sailplaneMassKg]/[ldRatio], applying the "bump to
     * next heavier class if L/D is insufficient" rule. [ldRatio] is optional per the AFM
     * workflow ("indien bekend") — when omitted, the mass-based class is used with no L/D
     * check at all, since there's nothing to check against.
     */
    fun selectTowClass(data: PerformanceTowData, sailplaneMassKg: Double, ldRatio: Double?): TowClassSelectionResult {
        val classes = data.soloClassesByAscendingMass
        val massClassIndex = classes.indexOfFirst { sailplaneMassKg <= it.sailplaneMassMaxKg }
            .let { if (it == -1) classes.lastIndex else it }

        if (ldRatio == null) {
            return TowClassSelectionResult.Selected(classes[massClassIndex], bumpedUp = false)
        }

        for (i in massClassIndex..classes.lastIndex) {
            if (ldRatio >= classes[i].ldRatioMin) {
                return TowClassSelectionResult.Selected(classes[i], bumpedUp = i != massClassIndex)
            }
        }
        return TowClassSelectionResult.NoDataAvailable
    }

    /**
     * [surfaceCorrectionFactor] (default 1.0 = no-op) is NOT AFM data — the tow tables are
     * already baselined on grass ("level short dry grass runway", Suppl. 1 §5.2.3.1), and
     * the AFM publishes no reverse correction for taking off from asphalt instead. When the
     * caller supplies one (a personal estimate, e.g. inverting the main AFM's own minimum
     * +20% grass penalty), it's applied the same way the grass penalty is applied in
     * [PerformanceCalculator.calculateTakeoff]: after interpolation, before the margin factor.
     */
    fun calculateTowTakeoff(
        data: PerformanceTowData,
        corrections: PerformanceCorrectionsData,
        sailplaneMassKg: Double,
        ldRatio: Double?,
        instructionFlight: Boolean,
        towplaneMassKg: Double,
        oatC: Double,
        pressureAltM: Double,
        headwindKts: Double,
        slopePct: Double,
        marginFactor: Double,
        surfaceCorrectionFactor: Double = 1.0
    ): TowTakeoffResult {
        val blockReasons = mutableListOf<TowBlockReason>()

        if (headwindKts < 0.0) {
            blockReasons += TowBlockReason.TailwindNotSupported
        }

        val maxSailplaneMass = if (instructionFlight) {
            data.limits.maxTowedSailplaneMassDualInstructionKg
        } else {
            data.limits.maxTowedSailplaneMassKg
        }
        if (sailplaneMassKg > maxSailplaneMass) {
            blockReasons += TowBlockReason.SailplaneMassExceeded(sailplaneMassKg, maxSailplaneMass, instructionFlight)
        }

        val maxTowplaneMass = if (instructionFlight) {
            data.limits.maxTowplaneTakeoffMassDualInstructionKg
        } else {
            data.limits.maxTowplaneTakeoffMassSoloKg
        }
        if (towplaneMassKg > maxTowplaneMass) {
            blockReasons += TowBlockReason.TowplaneMassExceeded(towplaneMassKg, maxTowplaneMass, instructionFlight)
        }

        if (blockReasons.isNotEmpty()) return TowTakeoffResult.blocked(blockReasons)

        val (selectedClass, bumpedUp) = if (instructionFlight) {
            val instructionClass = data.instructionClass
                ?: return TowTakeoffResult.blocked(listOf(TowBlockReason.InstructionClassMissing))
            instructionClass to false
        } else {
            when (val selection = selectTowClass(data, sailplaneMassKg, ldRatio)) {
                TowClassSelectionResult.NoDataAvailable ->
                    return TowTakeoffResult.blocked(listOf(TowBlockReason.NoClassAvailable))
                is TowClassSelectionResult.Selected -> selection.towClass to selection.bumpedUp
            }
        }

        val s1 = GridInterpolation.windBilinear(
            headwindKts, oatC, pressureAltM, selectedClass.table,
            windOf = { it.headwindKts }, oatOf = { it.oatC }, altOf = { it.pressureAltM },
            valueOf = { it.s1M }
        )
        val s2 = GridInterpolation.windBilinear(
            headwindKts, oatC, pressureAltM, selectedClass.table,
            windOf = { it.headwindKts }, oatOf = { it.oatC }, altOf = { it.pressureAltM },
            valueOf = { it.s2M }
        )

        val slopeAdverse = slopePct > 0.0
        val slopeMultiplier = corrections.slopeCorrection.factor(slopePct, adverse = slopeAdverse)
        val s1Corrected = s1.value * surfaceCorrectionFactor * slopeMultiplier
        val s2Corrected = s2.value * surfaceCorrectionFactor * slopeMultiplier

        return TowTakeoffResult(
            s1M = s1Corrected,
            s2M = s2Corrected,
            s1WithMarginM = s1Corrected * marginFactor,
            s2WithMarginM = s2Corrected * marginFactor,
            marginFactor = marginFactor,
            surfaceCorrectionApplied = surfaceCorrectionFactor != 1.0,
            surfaceCorrectionFactor = surfaceCorrectionFactor,
            slopeApplied = slopeAdverse,
            slopePct = slopePct,
            outOfRangeWarning = s1.outOfRange || s2.outOfRange,
            blocked = false,
            blockReasons = emptyList(),
            selectedClassId = selectedClass.id,
            classBumpedUp = bumpedUp
        )
    }
}
