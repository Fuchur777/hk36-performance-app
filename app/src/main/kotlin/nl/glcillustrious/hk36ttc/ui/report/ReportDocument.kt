package nl.glcillustrious.hk36ttc.ui.report

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
 */
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
    data class Section(val heading: String, val rows: List<Row>)

    /**
     * One label/value line. [value] is null for a line that is a statement rather than a
     * measurement (a warning, a status), which the renderer prints across the full width instead
     * of in two columns.
     */
    data class Row(val label: String, val value: String? = null, val emphasized: Boolean = false)
}
