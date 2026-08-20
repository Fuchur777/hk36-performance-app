package nl.schellenberg.hk36ttc.ui.report

import nl.schellenberg.hk36ttc.core.units.AppUnits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportDocumentBuilderTest {

    @Test
    fun `sections and rows accumulate in the order they were added`() {
        val doc = ReportDocumentBuilder().apply {
            section("Invoer") {
                row("OAT", "16 °C")
                row("Drukhoogte", "0 m")
            }
            section("Resultaat") {
                row("Grondloop", "182,0 m", emphasized = true)
            }
        }.build(title = "Take-off — PH-1600", timestamp = "17-08-2026 14:32", footer = "Geen EFB")

        assertEquals(listOf("Invoer", "Resultaat"), doc.sections.map { it.heading })
        assertEquals(listOf("OAT", "Drukhoogte"), doc.sections[0].rows.map { it.label })
        assertEquals("Take-off — PH-1600", doc.title)
        assertEquals("17-08-2026 14:32", doc.timestamp)
        assertTrue(doc.sections[1].rows.single().emphasized)
    }

    /** Lets a screen add a section unconditionally and let the content decide — runway results
     * only exist in airfield mode, so an empty one must not leave a stray heading in the PDF. */
    @Test
    fun `an empty section is dropped entirely`() {
        val doc = ReportDocumentBuilder().apply {
            section("Leeg") { }
            section("Gevuld") { row("A", "1") }
        }.build("t", "ts", "f")

        assertEquals(listOf("Gevuld"), doc.sections.map { it.heading })
    }

    @Test
    fun `rowIfPresent skips a null value but keeps a present one`() {
        val doc = ReportDocumentBuilder().apply {
            section("S") {
                rowIfPresent("Aanwezig", "1")
                rowIfPresent("Afwezig", null)
            }
        }.build("t", "ts", "f")

        assertEquals(listOf("Aanwezig"), doc.sections.single().rows.map { it.label })
    }

    @Test
    fun `a statement has no value so the renderer prints it full width`() {
        val doc = ReportDocumentBuilder().apply {
            section("S") { statement("Staartwind wordt niet ondersteund", emphasized = true) }
        }.build("t", "ts", "f")

        val row = doc.sections.single().rows.single()
        assertEquals(null, row.value)
        assertTrue(row.emphasized)
    }
}

class RunwayReportRowsTest {

    private val labels = RunwayRowLabels(
        surface = "Ondergrond",
        headwind = "Tegenwind",
        crosswind = "Kruiswind",
        crosswindExceeded = "boven gedemonstreerde waarde",
        groundRun = "Grondloop",
        obstacle = "Tot 15m obstakel",
        remaining = "Resterend",
        notCalculable = "Niet te berekenen (staartwind)"
    )

    private fun entry(
        label: String,
        groundRun: Double? = 180.0,
        obstacle: Double? = 280.0,
        remaining: Double? = 420.0,
        crosswindExceeded: Boolean = false
    ) = RunwayReportEntry(
        label = label,
        statusLabel = "Aanbevolen",
        surfaceLabel = "Gras (droog)",
        headwindKts = 6.0,
        crosswindKts = 2.0,
        crosswindExceeded = crosswindExceeded,
        groundRunWithMarginM = groundRun,
        obstacleWithMarginM = obstacle,
        remainingM = remaining
    )

    /**
     * The reason [PdfRenderer] has to page-break at all: a field like Terlet has six usable
     * directions, and every one of them must survive into the report.
     */
    @Test
    fun `every runway direction produces its own block`() {
        val entries = listOf("04C", "22C", "04L", "22R", "12", "30").map { entry(it) }

        val rows = runwayReportRows(entries, labels, AppUnits())

        val headers = rows.filter { it.value == null && it.emphasized }.map { it.label }
        assertEquals(6, headers.size)
        assertEquals("04C — Aanbevolen", headers.first())
        assertEquals("30 — Aanbevolen", headers.last())
    }

    @Test
    fun `a calculable direction lists surface, wind components and all three distances`() {
        val rows = runwayReportRows(listOf(entry("02")), labels, AppUnits())

        val labelsFound = rows.map { it.label.trim() }
        assertTrue(labelsFound.containsAll(listOf("Ondergrond", "Tegenwind", "Kruiswind", "Grondloop", "Tot 15m obstakel", "Resterend")))
        // Whole numbers, no decimals — matches the screen's own display rounding policy, and with
        // the default AppUnits() the figures stay in the same native metres/knots as before.
        assertEquals("180 m", rows.first { it.label.trim() == "Grondloop" }.value)
        assertEquals("280 m", rows.first { it.label.trim() == "Tot 15m obstakel" }.value)
    }

    /** A tailwind direction still gets a line, so the report shows it was considered rather than
     * silently leaving it out. */
    @Test
    fun `a direction without distances is reported as not calculable, not omitted`() {
        val rows = runwayReportRows(
            listOf(entry("20", groundRun = null, obstacle = null, remaining = null)),
            labels,
            AppUnits()
        )

        assertTrue(rows.any { it.label.trim() == "Niet te berekenen (staartwind)" })
        assertTrue(rows.none { it.label.trim() == "Grondloop" })
        assertEquals("20 — Aanbevolen", rows.first().label)
    }

    @Test
    fun `an exceeded crosswind is called out and emphasized on its own row`() {
        val rows = runwayReportRows(listOf(entry("02", crosswindExceeded = true)), labels, AppUnits())

        val crosswind = rows.first { it.label.trim() == "Kruiswind" }
        assertTrue(crosswind.emphasized)
        assertTrue(crosswind.value!!.contains("boven gedemonstreerde waarde"))
    }

    @Test
    fun `remaining runway carries an explicit sign, since the sign is the whole point`() {
        val positive = runwayReportRows(listOf(entry("02", remaining = 180.0)), labels, AppUnits())
        val negative = runwayReportRows(listOf(entry("02", remaining = -40.0)), labels, AppUnits())
        val zero = runwayReportRows(listOf(entry("02", remaining = 0.0)), labels, AppUnits())

        assertEquals("+180 m", positive.first { it.label.trim() == "Resterend" }.value)
        assertEquals("-40 m", negative.first { it.label.trim() == "Resterend" }.value)
        assertEquals("+0 m", zero.first { it.label.trim() == "Resterend" }.value)
    }
}
