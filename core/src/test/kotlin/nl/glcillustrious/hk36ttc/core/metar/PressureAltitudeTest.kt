package nl.glcillustrious.hk36ttc.core.metar

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class PressureAltitudeTest {

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) < tolerance, "expected=$expected actual=$actual")
    }

    @Test
    fun `standard QNH gives pressure altitude equal to field elevation`() {
        val result = PressureAltitude.fromElevationAndQnh(elevationM = 15.0, qnhHpa = 1013.25)

        assertApprox(15.0, result)
    }

    @Test
    fun `low QNH raises pressure altitude above field elevation`() {
        val result = PressureAltitude.fromElevationAndQnh(elevationM = 15.0, qnhHpa = 993.25)

        // 20 hPa below standard -> +20 * 8.23 m
        assertApprox(15.0 + 20.0 * 8.23, result)
    }

    @Test
    fun `high QNH lowers pressure altitude below field elevation`() {
        val result = PressureAltitude.fromElevationAndQnh(elevationM = 15.0, qnhHpa = 1033.25)

        // 20 hPa above standard -> -20 * 8.23 m
        assertApprox(15.0 - 20.0 * 8.23, result)
    }
}
