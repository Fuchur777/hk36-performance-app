package nl.schellenberg.hk36ttc.core.metar

/**
 * One usable runway direction to weigh against the current wind — e.g. "03" and "21" of the
 * same physical strip are two separate candidates with opposite headings and (per
 * rekenlogica.md) opposite-signed [slopePct].
 *
 * [designator] MUST be a stable, unique key (the caller is responsible for this — e.g. derived
 * from a database row id, never from user-entered free text). Two directions at the same field
 * can easily share the same *displayed* runway number (a grass "02" and an asphalt "02" are
 * common), so [label] carries whatever should actually be shown to the pilot, while
 * [designator] is what this advisor's own de-duplication/selection logic keys off of. Mixing
 * these up was a real bug once — a runway pick based on [label] silently resolved to whichever
 * same-labelled candidate happened to come first, using the wrong surface/slope for the
 * calculation.
 */
data class RunwayCandidate(
    val designator: String,
    val headingDegTrue: Double,
    val lengthM: Double,
    val slopePct: Double = 0.0,
    val label: String = designator
)

/**
 * The distance-to-15m-obstacle for one candidate, both with the pilot's current safety margin
 * and without any margin at all (factor 1.0) — the advisor needs both to tell "comfortably
 * fits" apart from "only fits if the margin is dropped entirely" (rekenlogica.md §5, Fase 2c
 * ronde 3: every runway is shown at once, colour-coded by which of these tiers it lands in,
 * rather than the pilot picking one runway from a plain ranked list).
 */
data class RequiredDistances(val withMarginM: Double, val withoutMarginM: Double)

enum class RunwayAdviceStatus {
    /** Fits with margin, and has the strongest headwind component among the directions that
     * do — remaining metres only break a tie in headwind, never outrank it. Airmanship: a
     * runway more aligned into wind is preferred over one with merely more spare length,
     * since crosswind carries handling risk (directional control, wingtip clearance) that a
     * distance-only comparison doesn't capture. */
    RECOMMENDED,

    /** Fits with margin, but isn't the best of the directions that do. */
    FITS,

    /** Does NOT fit with margin, but the bare (no-margin) distance still fits. */
    FITS_WITHOUT_MARGIN,

    /** Doesn't fit even without any margin at all. */
    DOES_NOT_FIT,

    /** Tailwind on this heading — the AFM tables have no tailwind data, so this direction
     * can't be evaluated at all, and is never picked as recommended regardless of length. */
    TAILWIND_NOT_SUPPORTED
}

/**
 * [requiredDistanceWithMarginM]/[requiredDistanceWithoutMarginM]/[remainingWithMarginM]/
 * [remainingWithoutMarginM] are null exactly when [status] is
 * [RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED] — there's nothing to compare against a tailwind
 * heading the AFM doesn't cover.
 *
 * [headwindGustKts]/[crosswindGustKts] are the same decomposition run against the METAR's gust
 * speed instead of its steady speed, and are non-null only when the report carried a gust group
 * *and* the wind came from that report rather than being typed by hand. They are for display
 * only: every judgement this advisor makes — [status], the ranking, and [crosswindExceeded] —
 * stays on the steady wind, so the recommendation does not move when a gust is present. The
 * pilot sees the gust figure in brackets beside the steady one and weighs it themselves.
 */
data class RunwayAdvice(
    val candidate: RunwayCandidate,
    val headwindKts: Double,
    val crosswindKts: Double,
    val headwindGustKts: Double?,
    val crosswindGustKts: Double?,
    val crosswindExceeded: Boolean,
    val requiredDistanceWithMarginM: Double?,
    val requiredDistanceWithoutMarginM: Double?,
    val remainingWithMarginM: Double?,
    val remainingWithoutMarginM: Double?,
    val status: RunwayAdviceStatus
)

/**
 * Ranks every runway direction at a field against the current wind, so the pilot sees at a
 * glance which one to use — see rekenlogica.md §5/§8. Deliberately knows nothing about
 * take-off vs. landing vs. tow: the caller supplies [requiredDistances] (typically a closure
 * over [nl.schellenberg.hk36ttc.core.perf.PerformanceCalculator] or
 * [nl.schellenberg.hk36ttc.core.perf.TowPerformanceCalculator] plus that screen's other
 * inputs), taking the headwind component this advisor already resolved plus the full
 * [RunwayCandidate] being evaluated — callers need more than just [RunwayCandidate.slopePct]
 * from it: which candidate it is also identifies which strip's surface applies, since two
 * candidates here can belong to different physical strips with different surfaces.
 *
 * Fase 2c ronde 3: there is no longer a single "the" runway this returns — every candidate is
 * shown to the pilot at once (rekenlogica.md §5), so this ranks and tiers all of them instead
 * of the caller picking one.
 *
 * **Ranking within a tier**: by headwind component, strongest first, remaining metres as the
 * tiebreak — not by remaining metres first. For a single wind vector, more headwind and less
 * crosswind are the same direction of travel (they're complementary components of one vector),
 * so this is equivalently "least crosswind first"; it is deliberately not "most spare runway
 * first", which could recommend a long runway well off the wind over a shorter one nearly
 * straight down it.
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
        /** The report's gust speed, when it had one. Display only — see [RunwayAdvice]. */
        windGustKts: Double? = null,
        requiredDistances: (headwindKts: Double, candidate: RunwayCandidate) -> RequiredDistances
    ): List<RunwayAdvice> {
        val evaluated = candidates.map { candidate ->
            val wind = WindComponents.compute(windDirectionDeg, windSpeedKts, candidate.headingDegTrue)
            // Same decomposition at gust strength, carried alongside for display. Never fed
            // into any comparison below: the ranking must not shift when a gust appears.
            val gust = windGustKts?.let { WindComponents.compute(windDirectionDeg, it, candidate.headingDegTrue) }
            if (wind.headwindKts < 0.0) {
                RunwayAdvice(
                    candidate = candidate,
                    headwindKts = wind.headwindKts,
                    crosswindKts = wind.crosswindKts,
                    headwindGustKts = gust?.headwindKts,
                    crosswindGustKts = gust?.crosswindKts,
                    crosswindExceeded = wind.crosswindKts > demonstratedCrosswindKts,
                    requiredDistanceWithMarginM = null,
                    requiredDistanceWithoutMarginM = null,
                    remainingWithMarginM = null,
                    remainingWithoutMarginM = null,
                    status = RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED
                )
            } else {
                val required = requiredDistances(wind.headwindKts, candidate)
                val remainingWithMargin = candidate.lengthM - required.withMarginM
                val remainingWithoutMargin = candidate.lengthM - required.withoutMarginM
                val status = when {
                    remainingWithMargin >= 0.0 -> RunwayAdviceStatus.FITS
                    remainingWithoutMargin >= 0.0 -> RunwayAdviceStatus.FITS_WITHOUT_MARGIN
                    else -> RunwayAdviceStatus.DOES_NOT_FIT
                }
                RunwayAdvice(
                    candidate = candidate,
                    headwindKts = wind.headwindKts,
                    crosswindKts = wind.crosswindKts,
                    headwindGustKts = gust?.headwindKts,
                    crosswindGustKts = gust?.crosswindKts,
                    crosswindExceeded = wind.crosswindKts > demonstratedCrosswindKts,
                    requiredDistanceWithMarginM = required.withMarginM,
                    requiredDistanceWithoutMarginM = required.withoutMarginM,
                    remainingWithMarginM = remainingWithMargin,
                    remainingWithoutMarginM = remainingWithoutMargin,
                    status = status
                )
            }
        }

        // Headwind first, remaining metres only to break a tie — see the KDoc above for why.
        val intraTierOrder = compareByDescending<RunwayAdvice> { it.headwindKts }
            .thenByDescending { it.remainingWithMarginM ?: it.remainingWithoutMarginM ?: Double.NEGATIVE_INFINITY }

        val recommended = evaluated
            .filter { it.status == RunwayAdviceStatus.FITS }
            .minWithOrNull(intraTierOrder)

        return evaluated
            .map { if (it === recommended) it.copy(status = RunwayAdviceStatus.RECOMMENDED) else it }
            .sortedWith(compareBy<RunwayAdvice> { statusRank(it.status) }.then(intraTierOrder))
    }

    private fun statusRank(status: RunwayAdviceStatus): Int = when (status) {
        RunwayAdviceStatus.RECOMMENDED, RunwayAdviceStatus.FITS -> 0
        RunwayAdviceStatus.FITS_WITHOUT_MARGIN -> 1
        RunwayAdviceStatus.DOES_NOT_FIT -> 2
        RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED -> 3
    }
}
