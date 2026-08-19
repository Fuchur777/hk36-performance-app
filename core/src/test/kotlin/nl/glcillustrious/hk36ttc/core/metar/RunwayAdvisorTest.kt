package nl.glcillustrious.hk36ttc.core.metar

import kotlin.test.Test
import kotlin.test.assertEquals

class RunwayAdvisorTest {

    private val demonstratedCrosswindKts = 8.0 // arbitrary AFM-like figure for these tests

    /** Most tests don't care about the with/without-margin distinction, so both figures are
     * kept equal — the margin-tier-specific behavior gets its own dedicated test below. */
    private fun flat(value: Double): RequiredDistances = RequiredDistances(value, value)

    @Test
    fun `runways are ordered by remaining metres, most spare length first`() {
        val runway03 = RunwayCandidate("03", headingDegTrue = 30.0, lengthM = 1000.0)
        val runway09 = RunwayCandidate("09", headingDegTrue = 90.0, lengthM = 1000.0)

        // Wind straight down 03 (full headwind, short required distance -> most spare metres);
        // 09 gets no headwind help at all (pure crosswind -> longer required distance).
        val advice = RunwayAdvisor.advise(
            candidates = listOf(runway09, runway03),
            windDirectionDeg = 30.0,
            windSpeedKts = 15.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistances = { headwindKts, _ -> flat(500.0 - headwindKts * 10.0) }
        )

        assertEquals(listOf("03", "09"), advice.map { it.candidate.designator })
        assertEquals(RunwayAdviceStatus.RECOMMENDED, advice.first().status)
    }

    /**
     * The test above doesn't actually distinguish the ranking rule from a simpler "most spare
     * metres wins" rule, because its strongest-headwind runway also happens to have the most
     * remaining metres. This one sets up a genuine conflict: a long runway well off the wind
     * (lots of spare length despite mediocre headwind) against a short one nearly straight down
     * it (far less spare length, but by far the strongest headwind). Headwind must win.
     */
    @Test
    fun `headwind ranks above remaining metres, even when the longer runway has far more spare length`() {
        val nearlyIntoWind = RunwayCandidate("27", headingDegTrue = 0.0, lengthM = 800.0)
        val mostlyCrosswind = RunwayCandidate("18", headingDegTrue = 80.0, lengthM = 2000.0)

        val advice = RunwayAdvisor.advise(
            candidates = listOf(mostlyCrosswind, nearlyIntoWind),
            windDirectionDeg = 0.0,
            windSpeedKts = 15.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistances = { headwindKts, _ -> flat(500.0 - headwindKts * 10.0) }
        )

        val byDesignator = advice.associateBy { it.candidate.designator }
        // "18" has far more remaining metres despite its weak headwind — confirms this scenario
        // actually exercises the conflict, rather than both runways happening to agree.
        assertEquals(true, byDesignator.getValue("18").remainingWithMarginM!! > byDesignator.getValue("27").remainingWithMarginM!!)

        assertEquals(listOf("27", "18"), advice.map { it.candidate.designator })
        assertEquals(RunwayAdviceStatus.RECOMMENDED, advice.first().status)
        assertEquals("27", advice.first().candidate.designator)
    }

    @Test
    fun `a tailwind heading is excluded from recommended and never fits`() {
        val downwind = RunwayCandidate("21", headingDegTrue = 210.0, lengthM = 5000.0)
        val upwind = RunwayCandidate("03", headingDegTrue = 30.0, lengthM = 1000.0)

        val advice = RunwayAdvisor.advise(
            candidates = listOf(downwind, upwind),
            windDirectionDeg = 30.0,
            windSpeedKts = 10.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistances = { headwindKts, _ -> flat(500.0 - headwindKts * 10.0) }
        )

        val downwindAdvice = advice.first { it.candidate.designator == "21" }
        assertEquals(RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED, downwindAdvice.status)
        assertEquals(null, downwindAdvice.requiredDistanceWithMarginM)
        assertEquals(null, downwindAdvice.requiredDistanceWithoutMarginM)
        // Even with 5x the length, the tailwind runway must never outrank the fitting one.
        assertEquals("03", advice.first().candidate.designator)
    }

    @Test
    fun `no runway fitting even without margin still ranks them by least-bad shortfall`() {
        val short03 = RunwayCandidate("03", headingDegTrue = 30.0, lengthM = 400.0)
        val shorter09 = RunwayCandidate("09", headingDegTrue = 90.0, lengthM = 200.0)

        val advice = RunwayAdvisor.advise(
            candidates = listOf(shorter09, short03),
            windDirectionDeg = 30.0,
            windSpeedKts = 5.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistances = { _, _ -> flat(1000.0) }
        )

        assertEquals(listOf("03", "09"), advice.map { it.candidate.designator })
        assertEquals(RunwayAdviceStatus.DOES_NOT_FIT, advice[0].status)
        assertEquals(RunwayAdviceStatus.DOES_NOT_FIT, advice[1].status)
    }

    @Test
    fun `fits only once the margin is dropped gets its own tier, ranked between FITS and DOES_NOT_FIT`() {
        val marginalRunway = RunwayCandidate("03", headingDegTrue = 30.0, lengthM = 500.0)
        val comfortableRunway = RunwayCandidate("09", headingDegTrue = 90.0, lengthM = 900.0)
        val hopelessRunway = RunwayCandidate("15", headingDegTrue = 150.0, lengthM = 100.0)

        // Calm wind: headwind/crosswind are identical for every heading, so only the supplied
        // distances (keyed by designator here) drive the outcome.
        val advice = RunwayAdvisor.advise(
            candidates = listOf(marginalRunway, comfortableRunway, hopelessRunway),
            windDirectionDeg = 0.0,
            windSpeedKts = 0.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistances = { _, candidate ->
                when (candidate.designator) {
                    // Needs 600m with margin (doesn't fit in 500m) but only 450m bare (fits).
                    "03" -> RequiredDistances(withMarginM = 600.0, withoutMarginM = 450.0)
                    // Comfortably fits even with margin.
                    "09" -> RequiredDistances(withMarginM = 700.0, withoutMarginM = 550.0)
                    // Doesn't fit even bare.
                    else -> RequiredDistances(withMarginM = 300.0, withoutMarginM = 250.0)
                }
            }
        )

        assertEquals(listOf("09", "03", "15"), advice.map { it.candidate.designator })
        assertEquals(RunwayAdviceStatus.RECOMMENDED, advice[0].status)
        assertEquals(RunwayAdviceStatus.FITS_WITHOUT_MARGIN, advice[1].status)
        assertEquals(-100.0, advice[1].remainingWithMarginM) // 500 - 600
        assertEquals(50.0, advice[1].remainingWithoutMarginM) // 500 - 450
        assertEquals(RunwayAdviceStatus.DOES_NOT_FIT, advice[2].status)
    }

    @Test
    fun `a tie in remaining metres still yields exactly one recommended runway`() {
        val runwayA = RunwayCandidate("01", headingDegTrue = 10.0, lengthM = 1000.0)
        val runwayB = RunwayCandidate("19", headingDegTrue = 190.0, lengthM = 1000.0)

        // Calm wind: both directions have identical (zero) headwind and thus identical
        // required/remaining distance.
        val advice = RunwayAdvisor.advise(
            candidates = listOf(runwayA, runwayB),
            windDirectionDeg = 0.0,
            windSpeedKts = 0.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistances = { _, _ -> flat(500.0) }
        )

        assertEquals(1, advice.count { it.status == RunwayAdviceStatus.RECOMMENDED })
    }

    @Test
    fun `each direction's own candidate (slope included) is passed to the distance calculator`() {
        // Same strip, opposite directions: uphill one way is downhill the other, per
        // rekenlogica.md's sign convention for RunwayStripEntity.
        val uphill = RunwayCandidate("03", headingDegTrue = 30.0, lengthM = 1000.0, slopePct = 2.0)
        val downhill = RunwayCandidate("21", headingDegTrue = 210.0, lengthM = 1000.0, slopePct = -2.0)
        val seenSlopes = mutableListOf<Double>()

        // Calm wind so both directions are evaluated (no tailwind exclusion).
        RunwayAdvisor.advise(
            candidates = listOf(uphill, downhill),
            windDirectionDeg = 0.0,
            windSpeedKts = 0.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistances = { _, candidate -> seenSlopes += candidate.slopePct; flat(500.0) }
        )

        assertEquals(listOf(2.0, -2.0), seenSlopes)
    }

    /**
     * Gusts are carried for display only. The pilot sees the gust-strength components beside the
     * steady ones, but nothing the advisor decides may move because of them — otherwise a gusty
     * report would quietly re-rank the runways between one refresh and the next.
     */
    @Test
    fun `a gust adds display components without changing the advice`() {
        val runway03 = RunwayCandidate("03", headingDegTrue = 30.0, lengthM = 1000.0)
        val runway09 = RunwayCandidate("09", headingDegTrue = 90.0, lengthM = 1000.0)
        val candidates = listOf(runway09, runway03)
        val distances = { headwindKts: Double, _: RunwayCandidate -> flat(500.0 - headwindKts * 10.0) }

        val steady = RunwayAdvisor.advise(
            candidates = candidates,
            windDirectionDeg = 30.0,
            windSpeedKts = 15.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistances = distances
        )
        val gusting = RunwayAdvisor.advise(
            candidates = candidates,
            windDirectionDeg = 30.0,
            windSpeedKts = 15.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            windGustKts = 30.0,
            requiredDistances = distances
        )

        // Order, status, required distances and the crosswind verdict are all untouched.
        assertEquals(steady.map { it.candidate.designator }, gusting.map { it.candidate.designator })
        assertEquals(steady.map { it.status }, gusting.map { it.status })
        assertEquals(steady.map { it.remainingWithMarginM }, gusting.map { it.remainingWithMarginM })
        assertEquals(steady.map { it.crosswindExceeded }, gusting.map { it.crosswindExceeded })

        // Only the extra display fields appear, and they scale with the gust speed.
        assertEquals(listOf(null, null), steady.map { it.crosswindGustKts })
        val gustingRunway09 = gusting.first { it.candidate.designator == "09" }
        val steadyRunway09 = steady.first { it.candidate.designator == "09" }
        assertEquals(
            steadyRunway09.crosswindKts * 2.0,
            requireNotNull(gustingRunway09.crosswindGustKts),
            0.0001
        )
        assertEquals(
            steadyRunway09.headwindKts * 2.0,
            requireNotNull(gustingRunway09.headwindGustKts),
            0.0001
        )
    }
}
