package nl.glcillustrious.hk36ttc.ui.report

/**
 * Small builder for assembling a [ReportDocument] from a screen.
 *
 * The per-screen assembly deliberately lives in the screen files rather than here: that's where
 * `stringResource` is available, and it lets a report reuse the exact labels the pilot just read
 * on screen instead of a parallel set that could drift. What lives *here* is everything that can
 * be wrong without a compiler noticing — number formatting and the per-runway expansion — so it
 * can be unit tested without an Android runtime.
 *
 * Empty sections are dropped automatically, so a screen can add a section unconditionally and
 * let the content decide whether it appears (e.g. runway results only exist in airfield mode).
 */
class ReportDocumentBuilder {
    private val sections = mutableListOf<ReportDocument.Section>()

    fun section(heading: String, build: SectionBuilder.() -> Unit) {
        val rows = SectionBuilder().apply(build).rows
        if (rows.isNotEmpty()) sections += ReportDocument.Section(heading, rows)
    }

    fun build(title: String, timestamp: String, footer: String) =
        ReportDocument(title = title, timestamp = timestamp, sections = sections, footer = footer)

    class SectionBuilder {
        internal val rows = mutableListOf<ReportDocument.Row>()

        /** A measurement: label on the left, value on the right. */
        fun row(label: String, value: String, emphasized: Boolean = false) {
            rows += ReportDocument.Row(label, value, emphasized)
        }

        /** A statement rather than a measurement — a warning or status, printed full width. */
        fun statement(text: String, emphasized: Boolean = false) {
            rows += ReportDocument.Row(text, null, emphasized)
        }

        /** Skips the row entirely when [value] is null, so optional figures don't need an `if`
         * at every call site. */
        fun rowIfPresent(label: String, value: String?, emphasized: Boolean = false) {
            if (value != null) row(label, value, emphasized)
        }

        fun addAll(other: List<ReportDocument.Row>) {
            rows += other
        }
    }
}

/** One runway direction's outcome, already translated, ready to become report rows. */
data class RunwayReportEntry(
    val label: String,
    val statusLabel: String,
    val surfaceLabel: String,
    val headwindKts: Double,
    val crosswindKts: Double,
    val crosswindExceeded: Boolean,
    val groundRunWithMarginM: Double?,
    val obstacleWithMarginM: Double?,
    val remainingM: Double?
)

/** Column labels for [runwayReportRows], passed in because `:app`'s report layer has no
 * resources of its own at this level — see [ReportDocumentBuilder]'s note. */
data class RunwayRowLabels(
    val surface: String,
    val headwind: String,
    val crosswind: String,
    val crosswindExceeded: String,
    val groundRun: String,
    val obstacle: String,
    val remaining: String,
    val notCalculable: String
)

/**
 * Expands every runway direction into report rows. This is the part that makes a report run past
 * one page — six directions at a field like Terlet is normal — so [PdfRenderer] must page-break,
 * and this function must never silently drop a direction.
 *
 * A direction with no distances is a tailwind the AFM has no data for; it still gets a line, so
 * the report shows it was considered and rejected rather than omitting it without explanation.
 */
fun runwayReportRows(
    entries: List<RunwayReportEntry>,
    labels: RunwayRowLabels
): List<ReportDocument.Row> = buildList {
    entries.forEach { entry ->
        add(ReportDocument.Row("${entry.label} — ${entry.statusLabel}", null, emphasized = true))
        add(ReportDocument.Row("    ${labels.surface}", entry.surfaceLabel))
        add(ReportDocument.Row("    ${labels.headwind}", formatOneDecimal(entry.headwindKts) + " kt"))
        add(
            ReportDocument.Row(
                "    ${labels.crosswind}",
                formatOneDecimal(entry.crosswindKts) + " kt" +
                    if (entry.crosswindExceeded) " — ${labels.crosswindExceeded}" else "",
                emphasized = entry.crosswindExceeded
            )
        )
        val groundRun = entry.groundRunWithMarginM
        val obstacle = entry.obstacleWithMarginM
        if (groundRun != null && obstacle != null) {
            add(ReportDocument.Row("    ${labels.groundRun}", formatOneDecimal(groundRun) + " m"))
            add(ReportDocument.Row("    ${labels.obstacle}", formatOneDecimal(obstacle) + " m"))
            // Directly after the with-margin distances, matching the screen: remaining length is
            // derived from those, not from the raw figures.
            entry.remainingM?.let {
                add(ReportDocument.Row("    ${labels.remaining}", formatSigned(it) + " m"))
            }
        } else {
            add(ReportDocument.Row("    ${labels.notCalculable}", null))
        }
    }
}

/** Matches the one-decimal formatting the calculation screens already use on screen, so a
 * report and the screen it came from never disagree on a rounded value. */
fun formatOneDecimal(value: Double): String = "%.1f".format(value)

/** Remaining runway reads better with an explicit sign — "+180 m" vs "-40 m" — since the sign
 * is the whole point of that figure. */
fun formatSigned(value: Double): String =
    if (value >= 0) "+" + formatOneDecimal(value) else formatOneDecimal(value)
