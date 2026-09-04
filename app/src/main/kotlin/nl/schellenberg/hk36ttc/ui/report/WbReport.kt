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
    /** Section heading plus row labels for the aircraft's own settings (see [WbAircraftInfo]) —
     * reuses the exact labels the Add/Edit Plane screen already shows for these fields
     * (`profile_edit_*`), so the PDF never invents a second wording for the same figure. */
    val sectionAircraft: String,
    val emptyMass: String,
    val emptyMassCg: String,
    val mtow: String,
    val cgForwardLimit: String,
    val cgAftLimit: String,
    val fuelTank: String,
    val footer: String
)

/** The aircraft profile's own settings that feed the W&B calculation (empty mass, its CG
 * position, MTOW, envelope limits, tank type) — already converted to the pilot's display unit
 * by the caller, same as every other figure in this report. Null on [buildWbReport] exactly when
 * no profile could be loaded (`WbFormState.profileNotFound`), in which case the section is
 * skipped entirely rather than printed with placeholder values. */
data class WbAircraftInfo(
    val emptyMassDisplay: Int,
    val emptyMassCgDisplay: Int,
    val mtowDisplay: Int,
    val cgForwardLimitDisplay: Int,
    val cgAftLimitDisplay: Int,
    val fuelTankLabel: String
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
    /** Null exactly when no profile is loaded — see [WbAircraftInfo]'s KDoc. */
    aircraftInfo: WbAircraftInfo?,
    pilotDisplay: Int,
    copilotDisplay: Int,
    fuelDisplay: Int,
    baggageDisplay: Int,
    massSuffix: String,
    fuelSuffix: String,
    cgSuffix: String,
    result: WBResult?,
    totalMassDisplay: Int?,
    cgPositionDisplay: String?,
    marginToMtowDisplay: Int?,
    violationTexts: List<String>,
    warningTexts: List<String>
): ReportDocument = ReportDocumentBuilder().apply {
    // Placed before the pilot-entered inputs: it's the stable aircraft context the flight-
    // specific figures below are computed against, kept in its own section so the two never
    // read as one undifferentiated list (Frank's "should also contain the data from the
    // aircraft setting screen" requirement).
    if (aircraftInfo != null) {
        section(labels.sectionAircraft) {
            row(labels.emptyMass, "${aircraftInfo.emptyMassDisplay} $massSuffix")
            row(labels.emptyMassCg, "${aircraftInfo.emptyMassCgDisplay} $cgSuffix")
            row(labels.mtow, "${aircraftInfo.mtowDisplay} $massSuffix")
            row(labels.cgForwardLimit, "${aircraftInfo.cgForwardLimitDisplay} $cgSuffix")
            row(labels.cgAftLimit, "${aircraftInfo.cgAftLimitDisplay} $cgSuffix")
            row(labels.fuelTank, aircraftInfo.fuelTankLabel)
        }
    }

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
            // specifically, and printing both would be contradictory. Tone matches
            // WbScreen.WbResultCard's own 2-state (success/warning) scheme — this screen never
            // distinguishes a violation from a warning by colour on screen, so neither does the
            // PDF.
            if (result.violations.isEmpty() && result.warnings.isEmpty()) {
                statement(labels.withinEnvelope, tone = ReportDocument.RowTone.SUCCESS)
            }
        }
    }

    section(labels.sectionNotes) {
        if (violationTexts.isNotEmpty() || warningTexts.isNotEmpty()) {
            statement(labels.warningHeading, emphasized = true)
        }
        violationTexts.forEach { statement("• $it", emphasized = true, tone = ReportDocument.RowTone.WARNING) }
        warningTexts.forEach { statement("• $it", emphasized = true, tone = ReportDocument.RowTone.WARNING) }
    }
}.build(title = title, timestamp = timestamp, footer = labels.footer)
