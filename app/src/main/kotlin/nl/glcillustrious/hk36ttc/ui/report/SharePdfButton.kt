package nl.glcillustrious.hk36ttc.ui.report

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.ui.common.FileSharing

/**
 * The "share this calculation as a PDF" button that sits at the bottom of all four calculation
 * screens.
 *
 * [buildDocument] is invoked at tap time rather than on every recomposition — the screens
 * recalculate continuously (no confirmation step since Fase 2c ronde 3), so building a report on
 * each keystroke would be pure waste. It receives the timestamp so the text inside the PDF and
 * the one in its filename are the same instant, not two calls to `now()` a few milliseconds
 * apart.
 *
 * [enabled] should be false whenever there is no result worth reporting (a blocked tow, a wind
 * the pilot hasn't entered yet) — a PDF of a calculation that never happened is misleading.
 */
@Composable
fun SharePdfButton(
    /** Short, filename-safe calculation kind, e.g. "takeoff". Not translated on purpose: a
     * filename should stay recognisable regardless of the app's language. */
    kind: String,
    registration: String?,
    enabled: Boolean,
    buildDocument: (timestampText: String) -> ReportDocument
) {
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.report_share_chooser_title)
    val timestampFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    OutlinedButton(
        onClick = {
            val now = LocalDateTime.now()
            val document = buildDocument(now.format(timestampFormatter))
            val safeRegistration = (registration ?: "HK36TTC").replace(Regex("[^A-Za-z0-9-]"), "")
            FileSharing.writeAndShare(
                context = context,
                fileName = "HK36TTC_${safeRegistration}_${kind}_${FileSharing.fileTimestamp(now)}.pdf",
                mimeType = FileSharing.MIME_PDF,
                chooserTitle = chooserTitle
            ) { out -> PdfRenderer.render(document, out) }
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
        Text(stringResource(R.string.report_share_pdf))
    }
}
