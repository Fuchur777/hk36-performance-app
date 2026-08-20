package nl.schellenberg.hk36ttc.ui.report

import nl.schellenberg.hk36ttc.core.wb.WBResult

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
 *
 * Every numeric input/result is already converted to the pilot's chosen display unit by the
 * caller, alongside the matching [massSuffix]/[fuelSuffix]/[cgSuffix] — this function stays a
 * pure formatter with no access to [nl.schellenberg.hk36ttc.core.units.AppUnits] or Compose,
 * same as it had no access to string resources before. [result] is kept only for the
 * violations/warnings-empty check; its own kg/mm fields are never read directly here, since
 * [totalMassDisplay]/[cgPositionDisplay]/[marginToMtowDisplay] already carry the converted
 * numbers that belong with it.
 *
 * `Int`, not `Double`: every converted quantity in the app is a whole number (see
 * [nl.schellenberg.hk36ttc.ui.common.displayMass]'s KDoc) — formatted directly here, same as
 * the take-off/landing/tow report paths ([buildPerformanceReport]) do for their own figures.
 */
fun buildWbReport(
    title: String,
    timestamp: String,
    labels: WbReportLabels,
    registration: String?,
    pilotDisplay: Int,
    copilotDisplay: Int,
    fuelDisplay: Int,
    baggageDisplay: Int,
    massSuffix: String,
    fuelSuffix: String,
    cgSuffix: String,
    result: WBResult?,
    totalMassDisplay: Int?,
    cgPositionDisplay: Int?,
    marginToMtowDisplay: Int?,
    violationTexts: List<String>,
    warningTexts: List<String>
): ReportDocument = ReportDocumentBuilder().apply {
    section(labels.sectionInput) {
        rowIfPresent(labels.registration, registration)
        row(labels.pilot, "$pilotDisplay $massSuffix")
        row(labels.copilot, "$copilotDisplay $massSuffix")
        row(labels.fuel, "$fuelDisplay $fuelSuffix")
        row(labels.baggage, "$baggageDisplay $massSuffix")
    }

    section(labels.sectionResult) {
        if (result != null && totalMassDisplay != null && cgPositionDisplay != null && marginToMtowDisplay != null) {
            row(labels.totalMass, "$totalMassDisplay $massSuffix", emphasized = true)
            row(labels.cg, "$cgPositionDisplay $cgSuffix", emphasized = true)
            val signedMargin = if (marginToMtowDisplay >= 0) "+$marginToMtowDisplay" else marginToMtowDisplay.toString()
            row(labels.marginToMtow, "$signedMargin $massSuffix")
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
