package nl.schellenberg.hk36ttc.ui.report

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A calculation, flattened into plain already-translated text, ready to be drawn as a PDF.
 *
 * Everything here is a `String` on purpose. `:core` has no access to Android resources (see the
 * `TowBlockReason` -> `stringResource` pattern), and the PDF renderer should know nothing about
 * aeroplanes — so the *screen* builds this model where `stringResource` is available, reusing
 * the exact labels the pilot just read on screen, and [PdfRenderer] only has to lay out text.
 *
 * That split also makes the builders (see `ReportBuilders.kt`) pure functions that can be unit
 * tested on the JVM without an Android runtime.
 *
 * [Serializable] so a calculation can also be persisted verbatim (see
 * `SavedCalculationEntity`/`AircraftProfileRepository.saveCalculation`) rather than shared
 * immediately — the exact same already-translated document that would have gone into a PDF at
 * save time is re-rendered unchanged later, with no need to re-run the calculation or re-resolve
 * any `stringResource` at share time.
 */
@Serializable
data class ReportDocument(
    /** e.g. "Take-off — PH-1600". */
    val title: String,
    /** When the calculation was made, already formatted for the active locale. Frank's explicit
     * requirement: a saved calculation is worthless as a record without knowing when it applied. */
    val timestamp: String,
    val sections: List<Section>,
    /** Closing note, in practice the "not a certified EFB" disclaimer from the About screen. */
    val footer: String
) {
    @Serializable
    data class Section(val heading: String, val rows: List<Row>)

    /**
     * One label/value line. [value] is null for a line that is a statement rather than a
     * measurement (a warning, a status), which the renderer prints across the full width instead
     * of in two columns.
     *
     * [tone] mirrors the same status colour the on-screen result card already shows for this row
     * (see e.g. `WbScreen.WbResultCard`, `RunwayResultCard`) — `null` for a row that is plain
     * data with no status of its own, e.g. an input value.
     */
    @Serializable
    data class Row(val label: String, val value: String? = null, val emphasized: Boolean = false, val tone: RowTone? = null)

    @Serializable
    enum class RowTone { SUCCESS, WARNING, ERROR }
}

/** Not pretty-printed, same reasoning as `data/export/RealLifeLogExport.kt`'s `realLifeLogJson`:
 * a [ReportDocument] is stored as one `saved_calculations` row, not read by hand, so indentation
 * would be pure overhead. [ignoreUnknownKeys] lets a document saved by a newer build still open
 * (and share) in an older one that hasn't learned a field it added. */
val reportDocumentJson = Json {
    ignoreUnknownKeys = true
}
