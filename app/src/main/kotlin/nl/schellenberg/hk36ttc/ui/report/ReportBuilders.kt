package nl.schellenberg.hk36ttc.ui.report

import nl.schellenberg.hk36ttc.core.metar.RunwayAdviceStatus
import nl.schellenberg.hk36ttc.core.units.AppUnits
import nl.schellenberg.hk36ttc.core.units.WindSpeedUnit
import nl.schellenberg.hk36ttc.ui.common.displayDistance
import nl.schellenberg.hk36ttc.ui.common.displayWindSpeed
import nl.schellenberg.hk36ttc.ui.common.distanceSuffix
import nl.schellenberg.hk36ttc.ui.common.windSpeedSuffix

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

        /** A measurement: label on the left, value on the right. [tone] mirrors the same
         * success/warning/error colour the on-screen result card already shows for this figure. */
        fun row(label: String, value: String, emphasized: Boolean = false, tone: ReportDocument.RowTone? = null) {
            rows += ReportDocument.Row(label, value, emphasized, tone)
        }

        /** A statement rather than a measurement — a warning or status, printed full width. */
        fun statement(text: String, emphasized: Boolean = false, tone: ReportDocument.RowTone? = null) {
            rows += ReportDocument.Row(text, null, emphasized, tone)
        }

        /** Skips the row entirely when [value] is null, so optional figures don't need an `if`
         * at every call site. */
        fun rowIfPresent(label: String, value: String?, emphasized: Boolean = false, tone: ReportDocument.RowTone? = null) {
            if (value != null) row(label, value, emphasized, tone)
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
    /** Gust-strength components, appended in brackets. Null when the report had no gust group;
     * the advice itself is always the steady-wind one. */
    val headwindGustKts: Double? = null,
    val crosswindGustKts: Double? = null,
    val crosswindExceeded: Boolean,
    val groundRunWithMarginM: Double?,
    val obstacleWithMarginM: Double?,
    val remainingM: Double?,
    /** Mirrors the same colour [nl.schellenberg.hk36ttc.ui.common.runwayStatusPresentation]
     * already gives this runway's card on screen — see [runwayStatusTone]. Null only makes
     * sense for a caller that deliberately has no on-screen colour to mirror; every screen that
     * builds runway entries always has one. */
    val tone: ReportDocument.RowTone? = null
)

/**
 * Maps the shared 4-tier on-screen colour scheme
 * ([nl.schellenberg.hk36ttc.ui.common.runwayStatusPresentation]) down to the PDF's 3-tone
 * palette: [RunwayAdviceStatus.RECOMMENDED] and [RunwayAdviceStatus.FITS] both render green
 * on screen (success/caution are both "still safe, just not the top pick") and both map to
 * [ReportDocument.RowTone.SUCCESS]; [RunwayAdviceStatus.FITS_WITHOUT_MARGIN] (orange) maps to
 * [ReportDocument.RowTone.WARNING]; [RunwayAdviceStatus.DOES_NOT_FIT] and
 * [RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED] (both red on screen) map to
 * [ReportDocument.RowTone.ERROR].
 */
fun runwayStatusTone(status: RunwayAdviceStatus): ReportDocument.RowTone = when (status) {
    RunwayAdviceStatus.RECOMMENDED, RunwayAdviceStatus.FITS -> ReportDocument.RowTone.SUCCESS
    RunwayAdviceStatus.FITS_WITHOUT_MARGIN -> ReportDocument.RowTone.WARNING
    RunwayAdviceStatus.DOES_NOT_FIT, RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED -> ReportDocument.RowTone.ERROR
}

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
    labels: RunwayRowLabels,
    units: AppUnits
): List<ReportDocument.Row> = buildList {
    val windSpeedSuf = windSpeedSuffix(units.windSpeed)
    val distanceSuf = distanceSuffix(units.distance)
    entries.forEach { entry ->
        add(ReportDocument.Row("${entry.label} — ${entry.statusLabel}", null, emphasized = true, tone = entry.tone))
        add(ReportDocument.Row("    ${labels.surface}", entry.surfaceLabel))
        add(
            ReportDocument.Row(
                "    ${labels.headwind}",
                "${displayWindSpeed(entry.headwindKts, units.windSpeed)} $windSpeedSuf" +
                    gustSuffix(entry.headwindGustKts, units.windSpeed)
            )
        )
        add(
            ReportDocument.Row(
                "    ${labels.crosswind}",
                "${displayWindSpeed(entry.crosswindKts, units.windSpeed)} $windSpeedSuf" +
                    gustSuffix(entry.crosswindGustKts, units.windSpeed) +
                    if (entry.crosswindExceeded) " — ${labels.crosswindExceeded}" else "",
                emphasized = entry.crosswindExceeded
            )
        )
        val groundRun = entry.groundRunWithMarginM
        val obstacle = entry.obstacleWithMarginM
        if (groundRun != null && obstacle != null) {
            add(ReportDocument.Row("    ${labels.groundRun}", "${displayDistance(groundRun, units.distance)} $distanceSuf"))
            add(ReportDocument.Row("    ${labels.obstacle}", "${displayDistance(obstacle, units.distance)} $distanceSuf"))
            // Directly after the with-margin distances, matching the screen: remaining length is
            // derived from those, not from the raw figures.
            entry.remainingM?.let {
                val remaining = displayDistance(it, units.distance)
                val signed = if (remaining >= 0) "+$remaining" else remaining.toString()
                add(ReportDocument.Row("    ${labels.remaining}", "$signed $distanceSuf"))
            }
        } else {
            add(ReportDocument.Row("    ${labels.notCalculable}", null))
        }
    }
}

/** " (18)" for a gust component, or "" when the report carried no gust group. Matches the
 * on-screen runway cards, which put the same figure in brackets after the steady one. */
private fun gustSuffix(gustKts: Double?, unit: WindSpeedUnit): String =
    if (gustKts == null) "" else " (" + displayWindSpeed(gustKts, unit) + ")"
