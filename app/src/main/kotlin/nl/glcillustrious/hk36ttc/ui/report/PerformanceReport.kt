package nl.glcillustrious.hk36ttc.ui.report

/**
 * Translated strings shared by the Take-off, Landing and Sleepvlucht reports. One bundle rather
 * than three near-identical ones: the three screens already share their labels on screen (all
 * three use `perf_*` strings), so the reports should not invent per-screen wording.
 */
data class PerformanceReportLabels(
    val registration: String,
    val sectionInput: String,
    val sectionWeather: String,
    val sectionResult: String,
    val sectionRunways: String,
    val sectionNotes: String,
    val airfield: String,
    val manualMode: String,
    val metar: String,
    val grassCondition: String,
    val oat: String,
    val pressureAlt: String,
    val headwind: String,
    val windDirection: String,
    val windSpeed: String,
    val surface: String,
    val slope: String,
    val marginFactor: String,
    val groundRun: String,
    val obstacle: String,
    val groundRunRaw: String,
    val obstacleRaw: String,
    val outOfRangeWarning: String,
    val tailwindNotSupported: String,
    val runwayRows: RunwayRowLabels,
    val footer: String
)

/** The weather/airfield context of a calculation, already resolved to display strings. */
data class PerformanceReportContext(
    val registration: String?,
    val airfieldName: String?,
    val metarRaw: String?,
    val grassConditionLabel: String?,
    val oatC: Int,
    val pressureAltM: Int,
    /** Null when this screen's calculation isn't headwind-indexed (Landing) or when the wind
     * came per-runway from a METAR rather than as one typed figure. */
    val headwindKts: Int?,
    val windDirectionDeg: Int?,
    val windSpeedKts: Int?,
    val surfaceLabel: String?,
    val slopePct: Int?,
    val marginFactorPct: Int
)

/** The single-value result shown when there is no per-runway list (plain Handmatig mode). */
data class PerformanceReportResult(
    val groundRunWithMarginM: Double,
    val obstacleWithMarginM: Double,
    val groundRunRawM: Double,
    val obstacleRawM: Double,
    val outOfRange: Boolean,
    val tailwindBlocked: Boolean
)

/**
 * Builds the report for any of the three performance screens.
 *
 * Either [result] (Handmatig: one calculation) or [runways] (airfield mode: every direction) is
 * populated — never both, mirroring what the screen itself shows. [extraNotes] carries whatever
 * that specific screen has to add, e.g. Sleepvlucht's block reasons or selected tow class.
 */
fun buildPerformanceReport(
    title: String,
    timestamp: String,
    labels: PerformanceReportLabels,
    context: PerformanceReportContext,
    result: PerformanceReportResult?,
    runways: List<RunwayReportEntry>,
    extraInputRows: List<ReportDocument.Row> = emptyList(),
    extraNotes: List<String> = emptyList()
): ReportDocument = ReportDocumentBuilder().apply {
    section(labels.sectionWeather) {
        rowIfPresent(labels.registration, context.registration)
        if (context.airfieldName != null) {
            row(labels.airfield, context.airfieldName)
        } else {
            statement(labels.manualMode)
        }
        row(labels.oat, "${context.oatC} °C")
        row(labels.pressureAlt, "${context.pressureAltM} m")
        context.windDirectionDeg?.let { row(labels.windDirection, "$it °") }
        context.windSpeedKts?.let { row(labels.windSpeed, "$it kt") }
        context.headwindKts?.let { row(labels.headwind, "$it kt") }
        rowIfPresent(labels.grassCondition, context.grassConditionLabel)
        // The raw METAR is included verbatim so the report stands on its own as evidence of what
        // the weather actually was, not just the numbers derived from it.
        rowIfPresent(labels.metar, context.metarRaw)
    }

    section(labels.sectionInput) {
        rowIfPresent(labels.surface, context.surfaceLabel)
        context.slopePct?.let { row(labels.slope, "$it %") }
        row(labels.marginFactor, "${context.marginFactorPct} %")
        addAll(extraInputRows)
    }

    section(labels.sectionResult) {
        if (result != null) {
            if (result.tailwindBlocked) {
                statement(labels.tailwindNotSupported, emphasized = true)
            } else {
                row(labels.groundRun, formatOneDecimal(result.groundRunWithMarginM) + " m", emphasized = true)
                row(labels.obstacle, formatOneDecimal(result.obstacleWithMarginM) + " m", emphasized = true)
                row(labels.groundRunRaw, formatOneDecimal(result.groundRunRawM) + " m")
                row(labels.obstacleRaw, formatOneDecimal(result.obstacleRawM) + " m")
            }
        }
    }

    section(labels.sectionRunways) {
        addAll(runwayReportRows(runways, labels.runwayRows))
    }

    section(labels.sectionNotes) {
        if (result?.outOfRange == true) statement(labels.outOfRangeWarning, emphasized = true)
        extraNotes.forEach { statement(it) }
    }
}.build(title = title, timestamp = timestamp, footer = labels.footer)
