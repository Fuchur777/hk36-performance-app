package nl.schellenberg.hk36ttc.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.schellenberg.hk36ttc.R

/**
 * [IntStepperField] plus a "terug naar standaard" action that only appears once the value has
 * drifted from [defaultValue] — so a user-adjusted margin factor (or any other JSON-sourced
 * default) can always be restored without hunting for the original number.
 */
@Composable
fun ResettableIntStepperField(
    label: String,
    value: Int,
    defaultValue: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    suffix: String
) {
    Column {
        IntStepperField(
            label = label, value = value, onValueChange = onValueChange,
            min = min, max = max, suffix = suffix
        )
        if (value != defaultValue) {
            TextButton(onClick = { onValueChange(defaultValue) }) {
                Text(stringResource(R.string.stepper_reset_to_default_format, defaultValue, suffix))
            }
        }
    }
}
