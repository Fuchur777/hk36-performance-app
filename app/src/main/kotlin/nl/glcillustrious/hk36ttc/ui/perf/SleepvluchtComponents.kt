package nl.glcillustrious.hk36ttc.ui.perf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.core.perf.PerformanceTowData
import nl.glcillustrious.hk36ttc.core.perf.SailplaneTypesData
import nl.glcillustrious.hk36ttc.core.perf.TowBlockReason
import nl.glcillustrious.hk36ttc.core.perf.TowTakeoffResult
import nl.glcillustrious.hk36ttc.ui.common.IntStepperField
import nl.glcillustrious.hk36ttc.ui.common.ResultRow
import nl.glcillustrious.hk36ttc.ui.theme.status

/**
 * Non-entry-point composables for [SleepvluchtScreen], split out here so the screen file itself
 * stays focused on layout/wiring. Visibility is `internal` (not `private`) purely because these
 * are used from a different file in the same package.
 */

/**
 * Auto-fills sleepgewicht/L-D from a favorited zweeftype (rekenlogica.md §2.3b) — shown as a
 * read-only card with an edit action, same pattern as [TowplaneMassField]. Falls back to the
 * plain manual steppers when no type is selected, or once the user chooses to override it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SailplaneTypeField(
    selectedTypeName: String?,
    usedFallback: Boolean,
    sailplaneMassKg: Int,
    ldRatioKnown: Boolean,
    ldRatio: Int,
    favoriteTypes: List<SailplaneTypesData.SailplaneType>,
    onSelectType: (SailplaneTypesData.SailplaneType) -> Unit,
    onEditManually: () -> Unit,
    onManualMassChange: (Int) -> Unit,
    onLdRatioKnownChange: (Boolean) -> Unit,
    onManualLdChange: (Int) -> Unit
) {
    if (selectedTypeName != null) {
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
                        Text(stringResource(R.string.sleepvlucht_glider_type_label), style = MaterialTheme.typography.labelLarge)
                        Text(selectedTypeName, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = onEditManually) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.sleepvlucht_edit_manually_content_description))
                    }
                }
                Text(
                    if (usedFallback) {
                        stringResource(R.string.sleepvlucht_type_detail_fallback_format, sailplaneMassKg, ldRatio)
                    } else {
                        stringResource(R.string.sleepvlucht_type_detail_mtow_format, sailplaneMassKg, ldRatio)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        if (favoriteTypes.isNotEmpty()) {
            FavoriteSailplaneTypeDropdown(favoriteTypes = favoriteTypes, onSelectType = onSelectType)
        }
        IntStepperField(
            label = stringResource(R.string.sleepvlucht_glider_weight_label), value = sailplaneMassKg,
            onValueChange = onManualMassChange,
            min = 100, max = 800, suffix = "kg"
        )
        LabeledCheckbox(
            label = stringResource(R.string.sleepvlucht_ld_known_label),
            checked = ldRatioKnown,
            onCheckedChange = onLdRatioKnownChange
        )
        if (ldRatioKnown) {
            IntStepperField(
                label = stringResource(R.string.sleepvlucht_ld_label), value = ldRatio,
                onValueChange = onManualLdChange,
                min = 10, max = 70, suffix = ""
            )
        }
    }
}

/**
 * Standard-looking Android dropdown field (styled like a read-only outlined text field with a
 * dropdown arrow) rather than a bare text link — avoids `ExposedDropdownMenuBox`, which doesn't
 * resolve against this project's Compose Material3 version, by overlaying a transparent
 * clickable box on a disabled [OutlinedTextField] to open the same [DropdownMenu] used
 * elsewhere in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoriteSailplaneTypeDropdown(
    favoriteTypes: List<SailplaneTypesData.SailplaneType>,
    onSelectType: (SailplaneTypesData.SailplaneType) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(R.string.sleepvlucht_favorite_type_label)) },
            placeholder = { Text(stringResource(R.string.sleepvlucht_favorite_type_placeholder)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { menuExpanded = true }
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            favoriteTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleepvlucht_favorite_type_option_format, type.name, type.mtowKg.toInt(), type.ldRatio.toString())) },
                    onClick = {
                        menuExpanded = false
                        onSelectType(type)
                    }
                )
            }
        }
    }
}

/**
 * Defaults to the last computed W&B weight for this registration (read-only, with an edit
 * action) so this field can't silently drift from what the aircraft actually weighs. Falls
 * back to a plain stepper if no W&B calculation has been done yet, or once the user chooses
 * to override it manually.
 */
@Composable
internal fun TowplaneMassField(
    fromWbKg: Int?,
    manualOverride: Boolean,
    manualKg: Int,
    onEditManually: () -> Unit,
    onUseWbValue: () -> Unit,
    onManualChange: (Int) -> Unit
) {
    if (fromWbKg != null && !manualOverride) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.sleepvlucht_towplane_weight_label), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(R.string.sleepvlucht_towplane_from_wb_format, fromWbKg),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEditManually) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.sleepvlucht_edit_manually_content_description))
                }
            }
        }
    } else {
        IntStepperField(
            label = stringResource(R.string.sleepvlucht_towplane_weight_label), value = manualKg,
            onValueChange = onManualChange,
            min = 600, max = 800, suffix = "kg"
        )
        if (fromWbKg != null) {
            TextButton(onClick = onUseWbValue) {
                Text(stringResource(R.string.sleepvlucht_towplane_reset_to_wb_format, fromWbKg))
            }
        }
    }
}

@Composable
internal fun LabeledCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}

@Composable
internal fun SleepvluchtResultCard(
    result: TowTakeoffResult,
    surfaceType: SleepvluchtSurfaceType,
    surfaceFactor: Double
) {
    if (result.blocked) {
        BlockedReasonsCard(result.blockReasons)
        return
    }
    val statusColors = MaterialTheme.status
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sleepvlucht_class_format, result.selectedClassId ?: ""), style = MaterialTheme.typography.labelLarge)
            if (result.classBumpedUp) {
                Text(
                    stringResource(R.string.sleepvlucht_class_bumped_up_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                stringResource(R.string.perf_margin_included_format, fmt(result.marginFactor)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.perf_result_surface_format, sleepvluchtSurfaceLabel(surfaceType)),
                style = MaterialTheme.typography.bodyMedium
            )
            ResultRow(stringResource(R.string.perf_ground_run_label), fmt(result.s1WithMarginM), "m")
            ResultRow(stringResource(R.string.perf_obstacle_15m_label), fmt(result.s2WithMarginM), "m")
            ResultRow(stringResource(R.string.perf_ground_run_raw_label), fmt(result.s1M), "m")
            ResultRow(stringResource(R.string.perf_obstacle_15m_raw_label), fmt(result.s2M), "m")
            val surfaceTag = if (surfaceType == SleepvluchtSurfaceType.DROOG_GRAS || surfaceType == SleepvluchtSurfaceType.ASFALT) {
                "[AFM]"
            } else {
                "[AIC P173]"
            }
            Text(
                stringResource(R.string.sleepvlucht_surface_toeslag_format, surfaceTag, fmt(surfaceFactor * 100)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (result.slopeApplied) {
                Text(
                    stringResource(R.string.sleepvlucht_slope_correction_format, fmt(result.slopePct)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.outOfRangeWarning) {
                Text(
                    stringResource(R.string.perf_out_of_range_warning),
                    color = statusColors.warning,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun towBlockReasonText(reason: TowBlockReason): String = when (reason) {
    TowBlockReason.TailwindNotSupported -> stringResource(R.string.perf_tailwind_not_supported)
    is TowBlockReason.SailplaneMassExceeded -> stringResource(
        if (reason.instructionFlight) R.string.tow_block_sailplane_mass_instruction_format
        else R.string.tow_block_sailplane_mass_solo_format,
        reason.actualKg, reason.maxKg
    )
    is TowBlockReason.TowplaneMassExceeded -> stringResource(
        if (reason.instructionFlight) R.string.tow_block_towplane_mass_instruction_format
        else R.string.tow_block_towplane_mass_solo_format,
        reason.actualKg, reason.maxKg
    )
    TowBlockReason.InstructionClassMissing -> stringResource(R.string.tow_block_instruction_class_missing)
    TowBlockReason.NoClassAvailable -> stringResource(R.string.tow_block_no_class_available)
}

@Composable
internal fun BlockedReasonsCard(reasons: List<TowBlockReason>) {
    val statusColors = MaterialTheme.status
    Card(
        colors = CardDefaults.cardColors(containerColor = statusColors.warning, contentColor = statusColors.onWarning),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.perf_warning_heading), fontWeight = FontWeight.Bold)
            reasons.forEach { reason -> Text("• ${towBlockReasonText(reason)}", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
internal fun SleepClimbReferenceCard(performanceTow: PerformanceTowData) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sleepvlucht_climb_heading), style = MaterialTheme.typography.labelLarge)
            performanceTow.climb.points.forEach { point ->
                Text(stringResource(R.string.sleepvlucht_climb_point_format, fmt(point.sailplaneMassKg), fmt(point.maxRateOfClimbMs)))
            }
            Text(
                stringResource(R.string.perf_no_correction_table_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
