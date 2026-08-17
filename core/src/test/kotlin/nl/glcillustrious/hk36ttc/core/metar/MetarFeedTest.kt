package nl.glcillustrious.hk36ttc.core.metar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetarFeedTest {

    /**
     * A real aviationweather.gov response (2026-08-17) for `ids=EHGR,EHAM,EHDL`. Two things this
     * locks in: every line carries the `METAR` report-type prefix, and the service answered in
     * its own order — EHDL first, though EHGR was requested first.
     */
    private val response = """
        METAR EHDL 170655Z AUTO 26005KT 170V320 5000 +DZ FEW003 SCT005 BKN016 15/14 Q1015 WHT
        METAR EHGR 170655Z AUTO VRB02KT 4000 -RA FEW004 SCT006 BKN011 15/15 Q1017 RERA GRN TEMPO 3000 RA BR SCT006 BKN010
        METAR EHAM 170655Z VRB02KT 9999 FEW009 SCT044 16/14 Q1015 NOSIG
    """.trimIndent()

    @Test
    fun `each report is filed under the station it names, not the order it was requested in`() {
        val byStation = MetarFeed.splitByStation(response)

        assertEquals(setOf("EHDL", "EHGR", "EHAM"), byStation.keys)
        assertTrue(byStation.getValue("EHGR").contains("VRB02KT"))
        assertTrue(byStation.getValue("EHDL").contains("26005KT"))
    }

    @Test
    fun `a station with no observation is simply absent rather than mismatched`() {
        // Terlet (EHTL) has no METAR station of its own; asking for it yields nothing back.
        val byStation = MetarFeed.splitByStation(response)

        assertFalse(byStation.containsKey("EHTL"))
    }

    @Test
    fun `unreadable lines are dropped instead of being filed under a wrong station`() {
        val withNoise = """
            No METAR found for XXXX
            METAR EHAM 170655Z VRB02KT 9999 FEW009 SCT044 16/14 Q1015 NOSIG
        """.trimIndent()

        assertEquals(setOf("EHAM"), MetarFeed.splitByStation(withNoise).keys)
    }

    @Test
    fun `an empty response yields an empty map`() {
        assertEquals(emptyMap(), MetarFeed.splitByStation("   \n  \n"))
    }
}
