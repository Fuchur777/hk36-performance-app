package nl.glcillustrious.hk36ttc.core.metar

/** One usable runway direction to weigh against the current wind — e.g. "03" and "21" of the
 * same physical strip are two separate candidates with opposite headings and (per
 * rekenlogica.md) opposite-signed [slopePct]. */
data class RunwayCandidate(
    val designator: String,
    val headingDegTrue: Double,
    val lengthM: Double,
    val slopePct: Double = 0.0
)

enum class RunwayAdviceStatus {
    /** Fits, and has the most remaining metres among the directions that fit. */
    PREFERRED,
    FITS,
    DOES_NOT_FIT,

    /** Tailwind on this heading — the AFM tables have no tailwind data, so this direction
     * can't be evaluated at all, and is never picked as preferred regardless of length. */
    TAILWIND_NOT_SUPPORTED
}

/** [requiredDistanceM]/[remainingM] are null exactly when [status] is
 * [RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED] — there's nothing to compare against a tailwind
 * heading the AFM doesn't cover. */
data class RunwayAdvice(
    val candidate: RunwayCandidate,
    val headwindKts: Double,
    val crosswindKts: Double,
    val crosswindExceeded: Boolean,
    val requiredDistanceM: Double?,
    val remainingM: Double?,
    val status: RunwayAdviceStatus
)

/**
 * Ranks every runway direction at a field against the current wind, so the pilot sees at a
 * glance which one to use — see rekenlogica.md §5/§8. Deliberately knows nothing about
 * take-off vs. landing vs. tow: the caller supplies [requiredDistanceM] (typically a closure
 * over [nl.glcillustrious.hk36ttc.core.perf.PerformanceCalculator] or
 * [nl.glcillustrious.hk36ttc.core.perf.TowPerformanceCalculator] plus that screen's other
 * inputs), taking the headwind component this advisor already resolved plus the full
 * [RunwayCandidate] being evaluated — callers need more than just [RunwayCandidate.slopePct]
 * from it: which candidate it is also identifies which strip's surface applies, since two
 * candidates here can belong to different physical strips with different surfaces. Returns the
 * AFM distance-to-15m-obstacle *with margin* (s2/l2-with-margin) — the safe figure to compare
 * against available runway length, not the bare ground roll.
 *
 * Known limitation: only runway *length* is compared — TODA/stopway aren't modeled, since the
 * saved airfield profile doesn't capture them (see docs/data/airfield_profile_schema.json).
 */
object RunwayAdvisor {

    fun advise(
        candidates: List<RunwayCandidate>,
        windDirectionDeg: Double,
        windSpeedKts: Double,
        demonstratedCrosswindKts: Double,
        requiredDistanceM: (headwindKts: Double, candidate: RunwayCandidate) -> Double
    ): List<RunwayAdvice> {
        val evaluated = candidates.map { candidate ->
            val wind = WindComponents.compute(windDirectionDeg, windSpeedKts, candidate.headingDegTrue)
            if (wind.headwindKts < 0.0) {
                RunwayAdvice(
                    candidate = candidate,
                    headwindKts = wind.headwindKts,
                    crosswindKts = wind.crosswindKts,
                    crosswindExceeded = wind.crosswindKts > demonstratedCrosswindKts,
                    requiredDistanceM = null,
                    remainingM = null,
                    status = RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED
                )
            } else {
                val required = requiredDistanceM(wind.headwindKts, candidate)
                val remaining = candidate.lengthM - required
                RunwayAdvice(
                    candidate = candidate,
                    headwindKts = wind.headwindKts,
                    crosswindKts = wind.crosswindKts,
                    crosswindExceeded = wind.crosswindKts > demonstratedCrosswindKts,
                    requiredDistanceM = required,
                    remainingM = remaining,
                    status = if (remaining >= 0.0) RunwayAdviceStatus.FITS else RunwayAdviceStatus.DOES_NOT_FIT
                )
            }
        }

        val preferred = evaluated
            .filter { it.status == RunwayAdviceStatus.FITS }
            .maxByOrNull { it.remainingM!! }

        return evaluated
            .map { if (it === preferred) it.copy(status = RunwayAdviceStatus.PREFERRED) else it }
            .sortedWith(
                compareByDescending<RunwayAdvice> { it.status != RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED }
                    .thenByDescending { it.remainingM ?: Double.NEGATIVE_INFINITY }
            )
    }
}
