package nl.glcillustrious.hk36ttc.core.airport

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One test per row of the conversion decision table in the plan — the rules that turn a messy
 * third-party file into runway data the performance calculation is allowed to trust.
 */
class RunwayCsvParserTest {

    private fun assertApprox(expected: Double, actual: Double, tolerance: Double = 1e-6) {
        assertTrue(abs(expected - actual) < tolerance, "expected=$expected actual=$actual")
    }

    /** Copied verbatim from the real `runways.csv` (2026-08-17), header included. */
    private val excerpt = """
        "id","airport_ref","airport_ident","length_ft","width_ft","surface","lighted","closed","le_ident","le_latitude_deg","le_longitude_deg","le_elevation_ft","le_heading_degT","le_displaced_threshold_ft","he_ident","he_latitude_deg","he_longitude_deg","he_elevation_ft","he_heading_degT","he_displaced_threshold_ft"
        269408,6523,"00A",80,80,"ASPH-G",1,0,"H1",,,,,,,,,,,
        254165,6525,"00AL",2100,90,"TURF",0,0,"01",,,,,,"19",,,,,
        245528,6528,"00CA",6000,80,"ASPH",0,0,"04",35.349300384521484,-116.89299774169922,,50,,"22",35.36029815673828,-116.87799835205078,,,
    """.trimIndent()

    private fun parse(text: String) = parseRunwaysCsv(text.lineSequence())

    private fun runwayCsv(vararg rows: String): String =
        (listOf(""""airport_ident","length_ft","surface","closed","le_ident","le_heading_degT","he_ident"""") + rows)
            .joinToString("\n")

    @Test
    fun `parses the real file layout`() {
        val runways = parse(excerpt)

        // "H1" is a helipad designator with no true heading — nothing to reconstruct from, so
        // it is dropped. The other two survive.
        assertEquals(listOf("00AL", "00CA"), runways.map { it.airportIdent })
    }

    @Test
    fun `a true heading from the source is used as-is and not marked derived`() {
        val runway = parse(excerpt).single { it.airportIdent == "00CA" }

        assertApprox(50.0, runway.headingDegTrueA)
        assertEquals(false, runway.headingDerived)
        assertEquals("04", runway.designatorA)
        assertEquals("22", runway.designatorB)
    }

    @Test
    fun `a missing heading is reconstructed from the designator and flagged as derived`() {
        val runway = parse(excerpt).single { it.airportIdent == "00AL" }

        assertApprox(10.0, runway.headingDegTrueA)
        assertTrue(runway.headingDerived, "a designator-derived heading must be flagged")
    }

    @Test
    fun `runway 36 derives to 0 degrees, staying inside the 0-359 range the UI allows`() {
        val runway = parse(runwayCsv(""""EHTL",900,"GRASS",0,"36",,"18"""")).single()

        assertApprox(0.0, runway.headingDegTrueA)
    }

    @Test
    fun `a designator suffix like L or R does not break the derivation`() {
        val runway = parse(runwayCsv(""""EHAM",11500,"ASPH",0,"27L",,"09R"""")).single()

        assertApprox(270.0, runway.headingDegTrueA)
        assertTrue(runway.headingDerived)
    }

    @Test
    fun `designators with nothing numeric to derive from are dropped, not stored as zero`() {
        val runways = parse(
            runwayCsv(
                """"00AK",2500,"GRVL",0,"N",,"S"""",
                """"00A",80,"ASPH",0,"H1",,""",
                """"BAD",1000,"ASPH",0,"99",,"""
            )
        )

        assertEquals(emptyList(), runways.map { it.airportIdent })
    }

    @Test
    fun `length is converted from feet to metres`() {
        val runway = parse(excerpt).single { it.airportIdent == "00AL" }

        assertApprox(2100 * 0.3048, runway.lengthM)
    }

    @Test
    fun `runways without a usable length are dropped`() {
        val runways = parse(
            runwayCsv(
                """"NOLEN",,"ASPH",0,"09",90,"27"""",
                """"ZEROLEN",0,"ASPH",0,"09",90,"27"""",
                """"OK",3000,"ASPH",0,"09",90,"27""""
            )
        )

        assertEquals(listOf("OK"), runways.map { it.airportIdent })
    }

    @Test
    fun `closed runways are dropped`() {
        val runways = parse(
            runwayCsv(
                """"SHUT",3000,"ASPH",1,"09",90,"27"""",
                """"OPEN",3000,"ASPH",0,"09",90,"27""""
            )
        )

        assertEquals(listOf("OPEN"), runways.map { it.airportIdent })
    }

    @Test
    fun `a missing high-end designator makes the strip one-way`() {
        val runway = parse(runwayCsv(""""EHTL",900,"GRASS",0,"07",70,""")).single()

        assertTrue(runway.oneWay)
        assertNull(runway.designatorB)
    }

    @Test
    fun `hard surfaces map to asphalt`() {
        for (raw in listOf("ASP", "ASPH", "ASPHALT", "CON", "CONC", "CONCRETE", "BIT", "TAR", "PEM")) {
            val runway = parse(runwayCsv(""""X",3000,"$raw",0,"09",90,"27"""")).single()
            assertEquals(ParsedRunwaySurface.ASPHALT, runway.surface, "surface=$raw")
        }
    }

    @Test
    fun `soft surfaces map to grass, whatever the spelling`() {
        for (raw in listOf("GRASS", "GRS", "TURF", "Turf", "GVL", "GRVL", "GRAVEL", "DIRT", "SAND")) {
            val runway = parse(runwayCsv(""""X",3000,"$raw",0,"09",90,"27"""")).single()
            assertEquals(ParsedRunwaySurface.GRASS, runway.surface, "surface=$raw")
        }
    }

    /**
     * The safety-critical default: an unrecognised surface must cost *more* distance, never
     * less. Grass carries at least a 20% penalty, asphalt none.
     */
    @Test
    fun `an unrecognised or missing surface falls back to grass, never asphalt`() {
        val unknown = parse(runwayCsv(""""X",3000,"SOMETHING-NEW",0,"09",90,"27"""")).single()
        assertEquals(ParsedRunwaySurface.GRASS, unknown.surface)

        val missing = parse(runwayCsv(""""X",3000,,0,"09",90,"27"""")).single()
        assertEquals(ParsedRunwaySurface.GRASS, missing.surface)
    }

    /** `UNPAVED` contains `PAVED`; mapping it to asphalt is the one error that must not happen. */
    @Test
    fun `UNPAVED is grass, not asphalt`() {
        val runway = parse(runwayCsv(""""X",3000,"UNPAVED",0,"09",90,"27"""")).single()

        assertEquals(ParsedRunwaySurface.GRASS, runway.surface)
    }

    @Test
    fun `a mixed hard-and-soft surface resolves to grass`() {
        val runway = parse(runwayCsv(""""X",3000,"ASPH-GRAVEL",0,"09",90,"27"""")).single()

        assertEquals(ParsedRunwaySurface.GRASS, runway.surface)
    }

    @Test
    fun `surfaces this aircraft cannot use at all are dropped rather than called grass`() {
        val runways = parse(
            runwayCsv(
                """"LAKE",3000,"WATER",0,"09",90,"27"""",
                """"COLD",3000,"SNOW",0,"09",90,"27"""",
                """"BERG",3000,"ICE",0,"09",90,"27"""",
                """"OK",3000,"ASPH",0,"09",90,"27""""
            )
        )

        assertEquals(listOf("OK"), runways.map { it.airportIdent })
    }

    @Test
    fun `an empty input yields an empty list`() {
        assertEquals(emptyList(), parseRunwaysCsv(emptySequence()))
    }
}
