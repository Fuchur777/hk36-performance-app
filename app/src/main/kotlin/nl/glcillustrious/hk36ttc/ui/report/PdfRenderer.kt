package nl.glcillustrious.hk36ttc.ui.report

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

/**
 * Draws a [ReportDocument] as a PDF using Android's own `android.graphics.pdf.PdfDocument`.
 *
 * No PDF library is pulled in for this: the platform can already do it, this app deliberately
 * carries no third-party dependencies beyond AndroidX/Compose/Room, and the obvious alternative
 * (iText) is AGPL unless licensed — a poor fit for a club-run app.
 *
 * Two layout requirements drive the shape of this code, both learned the hard way:
 * - **Page breaks.** A take-off at an airfield with six runway directions overflows one A4.
 * - **Line wrapping.** `Canvas.drawText` clips at the page edge without a word of warning, and
 *   the reports embed the raw METAR verbatim — 80 to 110 characters against a value column of
 *   roughly 220 points. The one line included as evidence of the actual weather was the one
 *   line guaranteed to be cut off.
 */
object PdfRenderer {

    /** A4 at 72 dpi, the unit `PdfDocument` works in. */
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    private const val TITLE_SIZE = 16f
    private const val HEADING_SIZE = 12f
    private const val BODY_SIZE = 10f
    private const val FOOTER_SIZE = 8f

    private const val LINE_HEIGHT = 15f
    private const val HEADING_SPACE_ABOVE = 14f
    private const val SECTION_SPACE_BELOW = 6f

    /** Where the value column starts, and the gap kept clear before it. */
    private const val VALUE_COLUMN_X = 300f
    private const val COLUMN_GAP = 12f

    fun render(document: ReportDocument, out: OutputStream) {
        val pdf = PdfDocument()
        val title = paint(TITLE_SIZE, bold = true)
        val heading = paint(HEADING_SIZE, bold = true)
        val body = paint(BODY_SIZE)
        val bodyBold = paint(BODY_SIZE, bold = true)
        val muted = paint(BODY_SIZE).apply { color = 0xFF555555.toInt() }
        val footerPaint = paint(FOOTER_SIZE).apply { color = 0xFF555555.toInt() }

        val bottomLimit = PAGE_HEIGHT - MARGIN
        val contentWidth = PAGE_WIDTH - 2 * MARGIN
        val labelWidth = VALUE_COLUMN_X - MARGIN - COLUMN_GAP
        val valueWidth = PAGE_WIDTH - MARGIN - VALUE_COLUMN_X

        var pageNumber = 1
        var page = pdf.startPage(pageInfo(pageNumber))
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            pdf.finishPage(page)
            pageNumber++
            page = pdf.startPage(pageInfo(pageNumber))
            canvas = page.canvas
            y = MARGIN
        }

        /** Reserves [needed] points of vertical space, starting a new page when it won't fit. */
        fun require(needed: Float) {
            if (y + needed > bottomLimit) newPage()
        }

        /**
         * Draws pre-wrapped [lines] at [x] from the current [y], leaving [y] one line-height per
         * line lower. Never breaks the page itself — callers reserve the space first, so a
         * two-column row can never end up split across two pages.
         */
        fun drawLines(lines: List<String>, x: Float, paint: Paint, lineHeight: Float) {
            var lineY = y
            lines.forEach { line ->
                canvas.drawText(line, x, lineY + paint.textSize, paint)
                lineY += lineHeight
            }
        }

        fun drawBlock(text: String, paint: Paint, lineHeight: Float) {
            val lines = wrapLines(text, contentWidth, paint)
            require(lines.size * lineHeight)
            drawLines(lines, MARGIN, paint, lineHeight)
            y += lines.size * lineHeight
        }

        drawBlock(document.title, title, TITLE_SIZE + 4f)
        y += 4f
        drawBlock(document.timestamp, muted, BODY_SIZE + 4f)
        y += 6f

        document.sections.forEach { section ->
            // Keep a heading with at least its first row, so a section never ends up stranded
            // alone at the bottom of a page.
            require(HEADING_SPACE_ABOVE + HEADING_SIZE + LINE_HEIGHT)
            y += HEADING_SPACE_ABOVE
            drawBlock(section.heading, heading, HEADING_SIZE + 3f)
            y += SECTION_SPACE_BELOW

            section.rows.forEach { row ->
                val linePaint = if (row.emphasized) bodyBold else body
                val value = row.value
                if (value == null) {
                    // A statement (warning, status) rather than a measurement: full width.
                    drawBlock(row.label, linePaint, LINE_HEIGHT)
                } else {
                    val labelLines = wrapLines(row.label, labelWidth, linePaint)
                    val valueLines = wrapLines(value, valueWidth, linePaint)
                    // Measure both columns, reserve the taller one, *then* draw — so a page
                    // break always happens before the row rather than through the middle of it.
                    val rowLines = maxOf(labelLines.size, valueLines.size)
                    require(rowLines * LINE_HEIGHT)
                    drawLines(labelLines, MARGIN, linePaint, LINE_HEIGHT)
                    drawLines(valueLines, VALUE_COLUMN_X, linePaint, LINE_HEIGHT)
                    y += rowLines * LINE_HEIGHT
                }
            }
        }

        require(LINE_HEIGHT * 2 + FOOTER_SIZE)
        y += LINE_HEIGHT
        document.footer.lineSequence().forEach { line ->
            drawBlock(line, footerPaint, FOOTER_SIZE + 4f)
        }

        pdf.finishPage(page)
        pdf.writeTo(out)
        pdf.close()
    }

    /** Wraps to [maxWidth] using [paint] to measure — the algorithm itself lives in
     * [TextWrapping] so it can be tested without a device. */
    private fun wrapLines(text: String, maxWidth: Float, paint: Paint): List<String> =
        TextWrapping.wrap(text) { paint.breakText(it, true, maxWidth, null) }

    private fun pageInfo(pageNumber: Int) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()

    private fun paint(size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
}
