package nl.glcillustrious.hk36ttc.ui.report

import nl.glcillustrious.hk36ttc.core.wb.WBResult

/** Translated strings for [buildWbReport], resolved by the screen. Keeping them in a parameter
 * object is what lets the builder below be a pure function with no Android dependency. */
data class WbReportLabels(
    val registration: String,
    val sectionInput: String,
    val sectionResult: String,
    val sectionNotes: String,
    val pilot: String,
    val copilot: String,
    val fuel: String,
    val baggage: String,
    val totalMass: String,
    val cg: String,
    val marginToMtow: String,
    val withinEnvelope: String,
    val warningHeading: String,
    val footer: String
)

/**
 * Turns a Weight & Balance calculation into a report.
 *
 * [violationTexts]/[warningTexts] arrive already translated: `WBViolation`/`WBWarning` are
 * `:core` sealed types with no access to resources, and `WbScreen` already owns the mapping to
 * localized strings — reusing it here means the PDF and the screen can never word the same
 * problem differently.
 */
fun buildWbReport(
    title: String,
    timestamp: String,
    labels: WbReportLabels,
    registration: String?,
    pilotKg: Int,
    copilotKg: Int,
    fuelLiters: Int,
    baggageKg: Int,
    result: WBResult?,
    violationTexts: List<String>,
    warningTexts: List<String>
): ReportDocument = ReportDocumentBuilder().apply {
    section(labels.sectionInput) {
        rowIfPresent(labels.registration, registration)
        row(labels.pilot, "$pilotKg kg")
        row(labels.copilot, "$copilotKg kg")
        row(labels.fuel, "$fuelLiters L")
        row(labels.baggage, "$baggageKg kg")
    }

    section(labels.sectionResult) {
        if (result != null) {
            row(labels.totalMass, formatOneDecimal(result.totalMassKg) + " kg", emphasized = true)
            row(labels.cg, formatOneDecimal(result.cgMm) + " mm", emphasized = true)
            row(labels.marginToMtow, formatSigned(result.marginToMtowKg) + " kg")
            // Only stated when it holds — a violation line below says the opposite far more
            // specifically, and printing both would be contradictory.
            if (result.violations.isEmpty() && result.warnings.isEmpty()) {
                statement(labels.withinEnvelope)
            }
        }
    }

    section(labels.sectionNotes) {
        if (violationTexts.isNotEmpty() || warningTexts.isNotEmpty()) {
            statement(labels.warningHeading, emphasized = true)
        }
        violationTexts.forEach { statement("• $it", emphasized = true) }
        warningTexts.forEach { statement("• $it", emphasized = true) }
    }
}.build(title = title, timestamp = timestamp, footer = labels.footer)
