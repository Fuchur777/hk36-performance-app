package nl.glcillustrious.hk36ttc.core.units

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Two kinds of proof for every conversion pair: a known reference value (catches a sign error or
 * a wrong factor outright), and a round-trip (catches an inverse that doesn't actually undo the
 * forward conversion). Both matter — a pair that round-trips perfectly but is off by a fixed
 * factor from reality would pass a round-trip-only test and still mislead every pilot who reads
 * the number.
 */
class UnitConversionsTest {

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 0.001) {
        assertTrue(abs(expected - actual) < tolerance, "expected $expected, got $actual")
    }

    // --- Temperature -----------------------------------------------------------------------

    @Test
    fun `temperature reference points`() {
        assertApprox(32.0, UnitConversions.celsiusToFahrenheit(0.0))
        assertApprox(212.0, UnitConversions.celsiusToFahrenheit(100.0))
        assertApprox(-40.0, UnitConversions.celsiusToFahrenheit(-40.0)) // the one point they agree
        assertApprox(15.0, UnitConversions.fahrenheitToCelsius(59.0))
    }

    @Test
    fun `temperature round-trips`() {
        for (c in listOf(-40.0, -20.0, 0.0, 15.0, 25.0, 45.0)) {
            assertApprox(c, UnitConversions.fahrenheitToCelsius(UnitConversions.celsiusToFahrenheit(c)))
        }
    }

    // --- Length (metres/feet — shared math for both Distance and Height) -------------------

    @Test
    fun `length reference points`() {
        assertApprox(3.28084, UnitConversions.metersToFeet(1.0))
        assertApprox(1000.0, UnitConversions.metersToFeet(304.8))
        assertApprox(1.0, UnitConversions.feetToMeters(3.28084))
    }

    @Test
    fun `length round-trips`() {
        for (m in listOf(0.0, 1.0, 15.0, 800.0, 1500.0, 3000.0)) {
            assertApprox(m, UnitConversions.feetToMeters(UnitConversions.metersToFeet(m)))
        }
    }

    // --- Wind speed (knots/m/s) --------------------------------------------------------------

    @Test
    fun `wind speed reference points`() {
        assertApprox(1.0, UnitConversions.knotsToMetersPerSecond(1.94384))
        assertApprox(10.29, UnitConversions.knotsToMetersPerSecond(20.0), tolerance = 0.01)
    }

    @Test
    fun `wind speed round-trips`() {
        for (kt in listOf(0.0, 5.0, 15.0, 30.0, 60.0)) {
            assertApprox(kt, UnitConversions.metersPerSecondToKnots(UnitConversions.knotsToMetersPerSecond(kt)))
        }
    }

    // --- Pressure (hPa/inHg) ------------------------------------------------------------------

    @Test
    fun `pressure reference point — standard atmosphere`() {
        // ISA standard pressure: 1013.25 hPa is the textbook 29.92 inHg.
        assertApprox(29.92, UnitConversions.hPaToInHg(1013.25), tolerance = 0.01)
    }

    @Test
    fun `pressure round-trips`() {
        for (hpa in listOf(950.0, 1000.0, 1013.25, 1030.0)) {
            assertApprox(hpa, UnitConversions.inHgToHPa(UnitConversions.hPaToInHg(hpa)))
        }
    }

    // --- Vertical speed (m/s / ft/min) --------------------------------------------------------

    @Test
    fun `vertical speed reference point`() {
        // A 1 m/s climb (a modest but real glider climb rate) is just under 197 ft/min.
        assertApprox(196.85, UnitConversions.metersPerSecondToFeetPerMinute(1.0), tolerance = 0.01)
    }

    @Test
    fun `vertical speed round-trips`() {
        for (ms in listOf(0.5, 1.0, 2.0, 3.0)) {
            assertApprox(
                ms,
                UnitConversions.feetPerMinuteToMetersPerSecond(UnitConversions.metersPerSecondToFeetPerMinute(ms))
            )
        }
    }

    // --- Airspeed (km/h / kt / mph) — kt<->km/h reuses core.metar.kmhToKts ------------------

    @Test
    fun `airspeed reference points`() {
        assertApprox(62.14, UnitConversions.kmhToMph(100.0), tolerance = 0.01)
        assertApprox(100.0, UnitConversions.mphToKmh(62.14), tolerance = 0.01)
    }

    @Test
    fun `airspeed round-trips`() {
        for (kmh in listOf(50.0, 90.0, 120.0, 200.0)) {
            assertApprox(kmh, UnitConversions.mphToKmh(UnitConversions.kmhToMph(kmh)))
        }
    }

    // --- Mass (kg/lbs) --------------------------------------------------------------------

    @Test
    fun `mass reference points`() {
        assertApprox(2.20462, UnitConversions.kgToLbs(1.0))
        assertApprox(1.0, UnitConversions.lbsToKg(2.20462))
    }

    @Test
    fun `mass round-trips`() {
        for (kg in listOf(0.0, 75.0, 265.0, 510.0, 720.0)) {
            assertApprox(kg, UnitConversions.lbsToKg(UnitConversions.kgToLbs(kg)))
        }
    }

    // --- CG position (mm/inch) --------------------------------------------------------------

    @Test
    fun `cg position reference points`() {
        assertApprox(1.0, UnitConversions.mmToInches(25.4))
        assertApprox(25.4, UnitConversions.inchesToMm(1.0))
    }

    @Test
    fun `cg position round-trips`() {
        for (mm in listOf(2300.0, 2350.0, 2450.0)) {
            assertApprox(mm, UnitConversions.inchesToMm(UnitConversions.mmToInches(mm)))
        }
    }

    // --- Fuel volume (litres/US gallons) ----------------------------------------------------

    @Test
    fun `fuel volume reference points`() {
        assertApprox(1.0, UnitConversions.litersToUsGallons(3.785411784))
        assertApprox(3.785411784, UnitConversions.usGallonsToLiters(1.0))
    }

    @Test
    fun `fuel volume round-trips`() {
        for (l in listOf(0.0, 20.0, 55.0, 80.0)) {
            assertApprox(l, UnitConversions.usGallonsToLiters(UnitConversions.litersToUsGallons(l)))
        }
    }
}
