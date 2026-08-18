package nl.glcillustrious.hk36ttc.ui.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the bug this code exists to prevent: reports embed the raw METAR verbatim, 80-110
 * characters against a value column of roughly 220 points. `Canvas.drawText` clips at the page
 * edge without any warning, so the one line included as evidence of the actual weather was the
 * one line guaranteed to be cut off.
 */
class TextWrappingTest {

    /** Stands in for `Paint.breakText`: a fixed-width font where [charsPerLine] characters fit. */
    private fun fixedWidth(charsPerLine: Int): (String) -> Int =
        { text -> minOf(text.length, charsPerLine) }

    @Test
    fun `text that already fits is left as one line`() {
        assertEquals(listOf("242,1 m"), TextWrapping.wrap("242,1 m", fixedWidth(20)))
    }

    /** The case that was silently clipped before. */
    @Test
    fun `a full raw METAR is broken into lines that all fit`() {
        val metar = "EHGR 170655Z AUTO VRB02KT 4000 -RA FEW004 SCT006 BKN011 15/15 Q1017 RERA GRN TEMPO 3000 RA BR SCT006 BKN010"

        val lines = TextWrapping.wrap(metar, fixedWidth(40))

        assertTrue(lines.size > 1, "a 100-character METAR must not stay on one line")
        assertTrue(lines.all { it.length <= 40 }, "every line must fit: $lines")
        // Nothing may be lost — the whole report hinges on this line being complete.
        assertEquals(metar.split(" "), lines.joinToString(" ").split(" "))
    }

    @Test
    fun `breaks happen at spaces so words stay intact`() {
        val lines = TextWrapping.wrap("Over 15m obstakel zonder marge", fixedWidth(18))

        assertTrue(lines.none { it.startsWith(" ") || it.endsWith(" ") })
        assertTrue(lines.all { line -> line.split(" ").none { it.isEmpty() } })
        assertEquals("Over 15m obstakel", lines.first())
    }

    /** Overflowing is invisible, so an unbreakable token is cut instead. */
    @Test
    fun `a single token longer than the line is broken mid-word rather than overflowing`() {
        val lines = TextWrapping.wrap("AAAAAAAAAAAAAAAAAAAAAAAAA", fixedWidth(10))

        assertEquals(listOf("AAAAAAAAAA", "AAAAAAAAAA", "AAAAA"), lines)
    }

    @Test
    fun `a mix of a long token and normal words still fits everywhere`() {
        val lines = TextWrapping.wrap("Station ZZZZZZZZZZZZZZZZZZZZ gemeld", fixedWidth(12))

        assertTrue(lines.all { it.length <= 12 }, "lines: $lines")
    }

    /** A measurer returning 0 (impossibly narrow column) must not spin forever. */
    @Test
    fun `a zero-width measurement still terminates, one character per line`() {
        val lines = TextWrapping.wrap("abc") { 0 }

        assertEquals(listOf("a", "b", "c"), lines)
    }

    @Test
    fun `empty text yields a single empty line rather than nothing`() {
        assertEquals(listOf(""), TextWrapping.wrap("", fixedWidth(10)))
    }
}
