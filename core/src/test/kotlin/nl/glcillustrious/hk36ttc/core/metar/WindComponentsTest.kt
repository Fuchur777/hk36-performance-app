package nl.glcillustrious.hk36ttc.core.metar

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class WindComponentsTest {

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) < tolerance, "expected=$expected actual=$actual")
    }

    @Test
    fun `wind straight down the runway is pure headwind`() {
        val result = WindComponents.compute(windDirectionDeg = 30.0, windSpeedKts = 10.0, runwayHeadingDegTrue = 30.0)

        assertApprox(10.0, result.headwindKts)
        assertApprox(0.0, result.crosswindKts)
    }

    @Test
    fun `wind exactly 90 degrees off is pure crosswind`() {
        val result = WindComponents.compute(windDirectionDeg = 120.0, windSpeedKts = 10.0, runwayHeadingDegTrue = 30.0)

        assertApprox(0.0, result.headwindKts)
        assertApprox(10.0, result.crosswindKts)
    }

    @Test
    fun `wind from behind is negative headwind (tailwind)`() {
        val result = WindComponents.compute(windDirectionDeg = 210.0, windSpeedKts = 10.0, runwayHeadingDegTrue = 30.0)

        assertApprox(-10.0, result.headwindKts)
        assertApprox(0.0, result.crosswindKts)
    }

    @Test
    fun `wind at 45 degrees splits evenly between head and crosswind`() {
        val result = WindComponents.compute(windDirectionDeg = 75.0, windSpeedKts = 10.0, runwayHeadingDegTrue = 30.0)

        val expected = 10.0 * sqrt(2.0) / 2.0
        assertApprox(expected, result.headwindKts)
        assertApprox(expected, result.crosswindKts)
    }

    @Test
    fun `wraparound near 360 degrees is handled correctly`() {
        // Wind 350, runway heading 010: only a 20-degree difference, not 340.
        val result = WindComponents.compute(windDirectionDeg = 350.0, windSpeedKts = 20.0, runwayHeadingDegTrue = 10.0)

        val expected = 20.0 * kotlin.math.cos(Math.toRadians(20.0))
        assertApprox(expected, result.headwindKts)
        assertTrue(result.headwindKts > 18.0, "expected a strongly positive headwind, got ${result.headwindKts}")
    }

    @Test
    fun `crosswind is always non-negative regardless of which side the wind is on`() {
        val fromLeft = WindComponents.compute(windDirectionDeg = 350.0, windSpeedKts = 10.0, runwayHeadingDegTrue = 30.0)
        val fromRight = WindComponents.compute(windDirectionDeg = 70.0, windSpeedKts = 10.0, runwayHeadingDegTrue = 30.0)

        assertTrue(fromLeft.crosswindKts > 0.0)
        assertTrue(fromRight.crosswindKts > 0.0)
    }
}
