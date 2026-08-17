package nl.glcillustrious.hk36ttc.core.airport

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AirportCsvParserTest {

    private fun assertApprox(expected: Double, actual: Double?, tolerance: Double = 1e-6) {
        val value = requireNotNull(actual) { "expected $expected, got null" }
        assertTrue(abs(expected - value) < tolerance, "expected=$expected actual=$value")
    }

    /**
     * Copied verbatim from the real `airports.csv` (2026-08-17), header included — same
     * approach as `SailplaneTypesDataTest`. Note the column order: `icao_code` sits *before*
     * `gps_code`, which is the reverse of the published OurAirports data dictionary. That
     * mismatch is the whole reason the reader resolves columns by name.
     */
    private val excerpt = """
        "id","ident","type","name","latitude_deg","longitude_deg","elevation_ft","continent","iso_country","iso_region","municipality","scheduled_service","icao_code","iata_code","gps_code","local_code","home_link","wikipedia_link","keywords"
        6523,"00A","heliport","Total RF Heliport",40.070985,-74.933689,11,"NA","US","US-PA","Bensalem","no",,,"K00A","00A",,,
        323361,"00AA","small_airport","Aero B Ranch Airport",38.704022,-101.473911,3435,"NA","US","US-KS","Leoti","no",,,"00AA","00AA",,,
        6528,"00CA","small_airport","Goldstone (GTS) Airport",35.35474,-116.885329,3038,"NA","US","US-CA","Barstow","no",,,"00CA","00CA",,,
    """.trimIndent()

    private fun parse(text: String) = parseAirportsCsv(text.lineSequence())

    @Test
    fun `parses the real file layout, resolving columns by name`() {
        val airports = parse(excerpt)

        assertEquals(3, airports.size)
        val first = airports[0]
        assertEquals("00A", first.ident)
        assertEquals("heliport", first.type)
        assertEquals("Total RF Heliport", first.name)
        assertEquals("US", first.isoCountry)
        assertEquals("Bensalem", first.municipality)
        // icao_code is empty for this row; gps_code is not. A positional reader would have
        // swapped these two.
        assertNull(first.icaoCode)
        assertEquals("K00A", first.gpsCode)
        assertEquals("00A", first.localCode)
    }

    @Test
    fun `elevation is converted from feet to metres`() {
        val airports = parse(excerpt)

        assertApprox(11 * 0.3048, airports[0].elevationM)
        assertApprox(3435 * 0.3048, airports[1].elevationM)
    }

    @Test
    fun `latitude and longitude are carried through for the future nearest-airfield feature`() {
        val first = parse(excerpt)[0]

        assertApprox(40.070985, first.latitudeDeg)
        assertApprox(-74.933689, first.longitudeDeg)
    }

    @Test
    fun `a name containing a comma survives, because the fields are quoted`() {
        val csv = """
            "ident","type","name","elevation_ft"
            "LEXJ","medium_airport","Ceuta, Helipuerto de",67
        """.trimIndent()

        assertEquals("Ceuta, Helipuerto de", parse(csv).single().name)
    }

    /**
     * An unknown elevation must not become 0: that would be a silent claim of sea level, and
     * it feeds straight into the pressure-altitude derivation and therefore every distance.
     */
    @Test
    fun `an empty elevation is null rather than zero`() {
        val csv = """
            "ident","type","name","elevation_ft"
            "EHTL","small_airport","Terlet",
        """.trimIndent()

        assertNull(parse(csv).single().elevationM)
    }

    @Test
    fun `closed airports are dropped`() {
        val csv = """
            "ident","type","name","elevation_ft"
            "AAAA","closed","Former Airfield",100
            "EHTL","small_airport","Terlet",164
        """.trimIndent()

        assertEquals(listOf("EHTL"), parse(csv).map { it.ident })
    }

    /**
     * The source is regenerated daily and may reorder or add columns at any time; this proves
     * nothing is positional.
     */
    @Test
    fun `a completely different column order parses identically`() {
        val csv = """
            "name","elevation_ft","type","extra_new_column","ident","icao_code"
            "Terlet",164,"small_airport","ignored","EHTL","EHTL"
        """.trimIndent()

        val airport = parse(csv).single()
        assertEquals("EHTL", airport.ident)
        assertEquals("Terlet", airport.name)
        assertEquals("EHTL", airport.icaoCode)
        assertApprox(164 * 0.3048, airport.elevationM)
    }

    @Test
    fun `rows without an ident or a name are skipped instead of throwing`() {
        val csv = """
            "ident","type","name","elevation_ft"
            ,"small_airport","No Ident",100
            "EHTL","small_airport",,164
            "EHGR","small_airport","Gilze-Rijen",49
        """.trimIndent()

        assertEquals(listOf("EHGR"), parse(csv).map { it.ident })
    }

    @Test
    fun `an empty input yields an empty list`() {
        assertEquals(emptyList(), parseAirportsCsv(emptySequence()))
    }
}
