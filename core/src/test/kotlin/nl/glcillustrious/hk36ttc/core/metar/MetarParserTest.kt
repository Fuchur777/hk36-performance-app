package nl.glcillustrious.hk36ttc.core.metar

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MetarParserTest {

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) < tolerance, "expected=$expected actual=$actual")
    }

    private fun parseOrFail(raw: String): ParsedMetar =
        when (val result = MetarParser.parse(raw)) {
            is MetarParseResult.Success -> result.metar
            is MetarParseResult.Failure -> fail("expected success, got failure: ${result.reason}")
        }

    @Test
    fun `standard METAR with gusts and hPa QNH parses correctly`() {
        val metar = parseOrFail("EHTL 161350Z 24012G20KT 9999 SCT025 18/12 Q1013 NOSIG")

        assertEquals("EHTL", metar.stationIcao)
        assertEquals(16, metar.observationDayOfMonth)
        assertEquals(13, metar.observationHourUtc)
        assertEquals(50, metar.observationMinuteUtc)
        assertEquals(240.0, metar.windDirectionDeg)
        assertEquals(false, metar.windVariableDirection)
        assertApprox(12.0, metar.windSpeedKts)
        assertApprox(20.0, requireNotNull(metar.windGustKts))
        assertApprox(18.0, metar.temperatureC)
        assertApprox(12.0, requireNotNull(metar.dewpointC))
        assertApprox(1013.0, requireNotNull(metar.qnhHpa))
    }

    @Test
    fun `negative temperature and dewpoint use the M prefix`() {
        val metar = parseOrFail("EHGR 010600Z 09008KT 9999 M03/M08 Q1029")

        assertApprox(-3.0, metar.temperatureC)
        assertApprox(-8.0, requireNotNull(metar.dewpointC))
    }

    @Test
    fun `variable wind direction has no headwind-usable direction`() {
        val metar = parseOrFail("EHDL 010600Z VRB03KT 9999 15/10 Q1015")

        assertNull(metar.windDirectionDeg)
        assertEquals(true, metar.windVariableDirection)
        assertApprox(3.0, metar.windSpeedKts)
    }

    @Test
    fun `calm wind reports zero speed and a usable zero direction`() {
        val metar = parseOrFail("EHDL 010600Z 00000KT 9999 15/10 Q1015")

        assertEquals(false, metar.windVariableDirection)
        assertApprox(0.0, requireNotNull(metar.windDirectionDeg))
        assertApprox(0.0, metar.windSpeedKts)
    }

    @Test
    fun `missing QNH still succeeds with a null value`() {
        val metar = parseOrFail("EHDL 010600Z 09008KT 9999 15/10")

        assertNull(metar.qnhHpa)
    }

    @Test
    fun `inHg altimeter is converted to hPa`() {
        val metar = parseOrFail("EHDL 010600Z 09008KT 9999 15/10 A2992")

        // 29.92 inHg is the standard-atmosphere reference value, ~1013.2 hPa.
        assertApprox(1013.2, requireNotNull(metar.qnhHpa), tolerance = 0.5)
    }

    @Test
    fun `wind speed in meters per second is converted to knots`() {
        val metar = parseOrFail("EHDL 010600Z 09010MPS 9999 15/10 Q1013")

        assertApprox(19.4384, metar.windSpeedKts, tolerance = 0.01)
    }

    @Test
    fun `empty input fails with Empty`() {
        val result = MetarParser.parse("   ")
        assertEquals(MetarParseResult.Failure(MetarParseError.Empty), result)
    }

    @Test
    fun `garbage input fails with MissingStation`() {
        val result = MetarParser.parse("this is not a metar at all")
        assertEquals(MetarParseResult.Failure(MetarParseError.MissingStation), result)
    }

    @Test
    fun `missing wind group fails with MissingWind`() {
        val result = MetarParser.parse("EHDL 010600Z 9999 15/10 Q1013")
        assertEquals(MetarParseResult.Failure(MetarParseError.MissingWind), result)
    }

    @Test
    fun `missing temperature group fails with MissingTemperature`() {
        val result = MetarParser.parse("EHDL 010600Z 09008KT 9999 Q1013")
        assertEquals(MetarParseResult.Failure(MetarParseError.MissingTemperature), result)
    }
}
