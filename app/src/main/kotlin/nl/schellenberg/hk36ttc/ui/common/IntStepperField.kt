package nl.schellenberg.hk36ttc.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.schellenberg.hk36ttc.R

/**
 * Whole-number kg/mm/liter picker used throughout the app instead of a free-text numeric
 * field: +/- buttons for one unit per tap, or tap the number to type an exact value
 * directly. Typing is never clamped mid-entry (so "600" isn't cut off to "400" after the
 * first keystroke) — [min]/[max] are only enforced once the field loses focus or a +/-
 * button is pressed. The label lives inside the text field itself to keep each row compact.
 */
@Composable
fun IntStepperField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    suffix: String,
    isError: Boolean = false,
    supportingText: String? = null,
    helperText: String? = null,
    step: Int = 1
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    // `onFocusChanged` also fires once when the field is first attached, reporting
    // "not focused" — without this guard that initial event runs the lose-focus clamp below
    // and calls `onValueChange` before the pilot has touched anything. For most fields that's
    // an invisible no-op (it writes the same value back), but a caller that treats any
    // `onValueChange` as "the pilot entered this" (Take-off's wind direction/speed, which set
    // `windManuallySet`) would see a phantom entry the instant the field appeared.
    var hasBeenFocused by remember { mutableStateOf(false) }

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilledTonalIconButton(onClick = { onValueChange((value - step).coerceIn(min, max)) }) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.stepper_decrease_content_description_format, label))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    val digits = newText.filter { it.isDigit() }
                    text = digits
                    digits.toIntOrNull()?.let(onValueChange)
                },
                label = { Text(label) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text(suffix) },
                isError = isError,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            hasBeenFocused = true
                        } else if (hasBeenFocused) {
                            val clamped = (text.toIntOrNull() ?: value).coerceIn(min, max)
                            text = clamped.toString()
                            onValueChange(clamped)
                        }
                    }
            )
            FilledTonalIconButton(onClick = { onValueChange((value + step).coerceIn(min, max)) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.stepper_increase_content_description_format, label))
            }
        }
        if (supportingText != null) {
            Text(
                supportingText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )
        } else if (helperText != null) {
            Text(
                helperText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            )
        }
    }
}
