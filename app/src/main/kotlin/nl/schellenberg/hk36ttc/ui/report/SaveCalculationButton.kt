package nl.schellenberg.hk36ttc.ui.report

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import nl.schellenberg.hk36ttc.R

/**
 * The "save this calculation to the list" button that replaces [SharePdfButton] at the bottom of
 * all four calculation screens (Fase: save-instead-of-share, mirroring the Real Life Performance
 * list — see `SavedCalculationListScreen`). Sharing a calculation as a PDF is still available,
 * just moved one level down: from the saved-calculations list, per entry, rather than immediately
 * here.
 *
 * Same "[buildDocument] invoked at tap time" reasoning as [SharePdfButton]: the screens
 * recalculate continuously, so building a report on every keystroke would be pure waste, and the
 * timestamp text embedded in the document must be the same instant it's saved under.
 */
@Composable
fun SaveCalculationButton(
    enabled: Boolean,
    buildDocument: (timestampText: String) -> ReportDocument,
    onSave: (ReportDocument) -> Unit
) {
    val timestampFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

    OutlinedButton(
        onClick = {
            val now = LocalDateTime.now()
            onSave(buildDocument(now.format(timestampFormatter)))
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Filled.Save, contentDescription = null)
        Text(stringResource(R.string.common_save))
    }
}
