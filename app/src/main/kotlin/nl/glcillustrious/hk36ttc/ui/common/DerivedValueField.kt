package nl.glcillustrious.hk36ttc.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.glcillustrious.hk36ttc.R

/**
 * A value the app filled in for you (from a selected airfield/runway/METAR) rather than
 * something you typed — read-only with an edit action until overridden, then shows a marker
 * plus a way back. This generalizes the pattern already used by `TowplaneMassField` and
 * `SailplaneTypeField` in `SleepvluchtComponents.kt` for exactly this "derived, but
 * overridable" shape; those two predate this file and haven't been migrated onto it, but any
 * new derived-value field should use this one instead of re-implementing the pattern.
 */
@Composable
fun DerivedValueField(
    label: String,
    valueText: String,
    isOverridden: Boolean,
    onEdit: () -> Unit,
    onResetToDerived: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Text(valueText, style = MaterialTheme.typography.bodyMedium)
                }
                if (isOverridden) {
                    TextButton(onClick = onResetToDerived) {
                        Text(stringResource(R.string.derived_value_reset))
                    }
                } else {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.derived_value_edit_content_description))
                    }
                }
            }
            if (isOverridden) {
                Text(
                    stringResource(R.string.derived_value_overridden_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
