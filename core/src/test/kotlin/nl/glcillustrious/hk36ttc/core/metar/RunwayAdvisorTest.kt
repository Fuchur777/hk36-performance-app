package nl.glcillustrious.hk36ttc.core.metar

import kotlin.test.Test
import kotlin.test.assertEquals

class RunwayAdvisorTest {

    private val demonstratedCrosswindKts = 8.0 // arbitrary AFM-like figure for these tests

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
            requiredDistanceM = { headwindKts, _ -> 500.0 - headwindKts * 10.0 }
        )

        assertEquals(listOf("03", "09"), advice.map { it.candidate.designator })
        assertEquals(RunwayAdviceStatus.PREFERRED, advice.first().status)
    }

    @Test
    fun `a tailwind heading is excluded from preferred and never fits`() {
        val downwind = RunwayCandidate("21", headingDegTrue = 210.0, lengthM = 5000.0)
        val upwind = RunwayCandidate("03", headingDegTrue = 30.0, lengthM = 1000.0)

        val advice = RunwayAdvisor.advise(
            candidates = listOf(downwind, upwind),
            windDirectionDeg = 30.0,
            windSpeedKts = 10.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistanceM = { headwindKts, _ -> 500.0 - headwindKts * 10.0 }
        )

        val downwindAdvice = advice.first { it.candidate.designator == "21" }
        assertEquals(RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED, downwindAdvice.status)
        assertEquals(null, downwindAdvice.requiredDistanceM)
        // Even with 5x the length, the tailwind runway must never outrank the fitting one.
        assertEquals("03", advice.first().candidate.designator)
    }

    @Test
    fun `no runway fitting still ranks them by least-bad shortfall`() {
        val short03 = RunwayCandidate("03", headingDegTrue = 30.0, lengthM = 400.0)
        val shorter09 = RunwayCandidate("09", headingDegTrue = 90.0, lengthM = 200.0)

        val advice = RunwayAdvisor.advise(
            candidates = listOf(shorter09, short03),
            windDirectionDeg = 30.0,
            windSpeedKts = 5.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistanceM = { _, _ -> 1000.0 }
        )

        assertEquals(listOf("03", "09"), advice.map { it.candidate.designator })
        assertEquals(RunwayAdviceStatus.DOES_NOT_FIT, advice[0].status)
        assertEquals(RunwayAdviceStatus.DOES_NOT_FIT, advice[1].status)
    }

    @Test
    fun `a tie in remaining metres still yields exactly one preferred runway`() {
        val runwayA = RunwayCandidate("01", headingDegTrue = 10.0, lengthM = 1000.0)
        val runwayB = RunwayCandidate("19", headingDegTrue = 190.0, lengthM = 1000.0)

        // Calm wind: both directions have identical (zero) headwind and thus identical
        // required/remaining distance.
        val advice = RunwayAdvisor.advise(
            candidates = listOf(runwayA, runwayB),
            windDirectionDeg = 0.0,
            windSpeedKts = 0.0,
            demonstratedCrosswindKts = demonstratedCrosswindKts,
            requiredDistanceM = { _, _ -> 500.0 }
        )

        assertEquals(1, advice.count { it.status == RunwayAdviceStatus.PREFERRED })
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
            requiredDistanceM = { _, candidate -> seenSlopes += candidate.slopePct; 500.0 }
        )

        assertEquals(listOf(2.0, -2.0), seenSlopes)
    }
}
