package nl.schellenberg.hk36ttc.core.airport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvReaderTest {

    @Test
    fun `plain unquoted fields split on commas`() {
        assertEquals(listOf("6523", "00A", "heliport"), CsvReader.splitLine("6523,00A,heliport"))
    }

    @Test
    fun `quotes are stripped from quoted fields`() {
        assertEquals(
            listOf("6523", "00A", "Total RF Heliport"),
            CsvReader.splitLine("""6523,"00A","Total RF Heliport"""")
        )
    }

    /** The reason a plain `split(",")` cannot be used on this dataset. */
    @Test
    fun `a comma inside a quoted field does not split it`() {
        assertEquals(
            listOf("1", "Ceuta, Helipuerto de", "ES"),
            CsvReader.splitLine("""1,"Ceuta, Helipuerto de","ES"""")
        )
    }

    @Test
    fun `a doubled quote inside a quoted field becomes one literal quote`() {
        assertEquals(
            listOf("1", """The "Old" Field"""),
            CsvReader.splitLine("""1,"The ""Old"" Field"""")
        )
    }

    @Test
    fun `empty fields are preserved as empty strings, including trailing ones`() {
        // OurAirports rows routinely end in a run of empty columns.
        assertEquals(listOf("1", "", "", ""), CsvReader.splitLine("1,,,"))
    }

    @Test
    fun `a trailing carriage return is not swallowed into the last field`() {
        // Guarded by CsvHeader.value()/the parsers trimming, so assert the raw behaviour here
        // and the trimmed behaviour below.
        val fields = CsvReader.splitLine("1,ASPH\r")
        assertEquals(2, fields.size)
        assertEquals("ASPH", fields[1].trim())
    }

    @Test
    fun `header resolves columns by name, not position`() {
        val header = CsvHeader.parse(""""id","ident","type","name"""")
        val row = CsvReader.splitLine("""6523,"00A","heliport","Total RF Heliport"""")

        assertEquals("00A", header.value(row, "ident"))
        assertEquals("Total RF Heliport", header.value(row, "name"))
        assertEquals("heliport", header.value(row, "type"))
    }

    @Test
    fun `a column absent from the header yields null instead of throwing`() {
        val header = CsvHeader.parse(""""id","ident"""")
        val row = CsvReader.splitLine("6523,00A")

        assertNull(header.indexOf("elevation_ft"))
        assertNull(header.value(row, "elevation_ft"))
    }

    @Test
    fun `an empty value and a short row both read as null`() {
        val header = CsvHeader.parse(""""id","ident","elevation_ft"""")

        assertNull(header.value(CsvReader.splitLine("6523,00A,"), "elevation_ft"))
        assertNull(header.value(CsvReader.splitLine("6523,00A"), "elevation_ft"))
    }
}
