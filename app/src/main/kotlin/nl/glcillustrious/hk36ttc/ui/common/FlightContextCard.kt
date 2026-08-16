package nl.glcillustrious.hk36ttc.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.core.metar.MetarParseResult
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdvice
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdviceStatus
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextMode
import nl.glcillustrious.hk36ttc.data.local.GrassCondition
import nl.glcillustrious.hk36ttc.ui.theme.status

/**
 * The Vliegveld/Handmatig toggle plus everything that follows from picking "Vliegveld",
 * shared by the Take-off/Landing/Sleepvlucht screens (rekenlogica.md §5). Deliberately knows
 * nothing about OAT/pressure-altitude/surface derivation — each screen's own ViewModel builds
 * [runwayAdvice] (via [nl.glcillustrious.hk36ttc.core.metar.RunwayAdvisor], using that screen's
 * own distance calculator) and turns the confirmed choice into its own form fields. This card
 * only handles: which mode, which airfield, today's grass condition, which runway, and the
 * mandatory confirmation gate before a result may be shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightContextCard(
    mode: FlightContextMode,
    onModeChange: (FlightContextMode) -> Unit,
    airfields: List<AirfieldEntity>,
    selectedAirfield: AirfieldEntity?,
    onSelectAirfield: (AirfieldEntity) -> Unit,
    hasGrassRunway: Boolean,
    grassCondition: GrassCondition,
    onGrassConditionChange: (GrassCondition) -> Unit,
    runwayAdvice: List<RunwayAdvice>,
    chosenDesignator: String?,
    onChooseRunway: (String?) -> Unit,
    metarParsed: MetarParseResult?,
    metarConfig: MetarConfigData,
    confirmed: Boolean,
    onConfirm: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.flight_context_mode_label), style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
                FlightContextMode.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = { onModeChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = FlightContextMode.entries.size),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            when (option) {
                                FlightContextMode.AIRFIELD -> stringResource(R.string.flight_context_mode_airfield)
                                FlightContextMode.MANUAL -> stringResource(R.string.flight_context_mode_manual)
                            }
                        )
                    }
                }
            }

            if (mode == FlightContextMode.AIRFIELD) {
                if (airfields.isEmpty()) {
                    Text(
                        stringResource(R.string.flight_context_no_airfields_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AirfieldDropdown(airfields, selectedAirfield, onSelectAirfield)

                    if (selectedAirfield == null) {
                        Text(
                            stringResource(R.string.flight_context_choose_airfield_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        if (hasGrassRunway) {
                            GrassConditionSelector(grassCondition, onGrassConditionChange)
                        }
                        MetarSummary(metarParsed, selectedAirfield.elevationM.roundToInt(), metarConfig)
                        RunwayAdviceList(runwayAdvice, chosenDesignator, onChooseRunway)
                        FlightContextConfirmRow(confirmed, onConfirm)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirfieldDropdown(
    airfields: List<AirfieldEntity>,
    selected: AirfieldEntity?,
    onSelect: (AirfieldEntity) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(R.string.flight_context_choose_airfield_label)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.matchParentSize().clickable { menuExpanded = true })
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            airfields.forEach { airfield ->
                DropdownMenuItem(
                    text = { Text(airfield.name) },
                    onClick = {
                        menuExpanded = false
                        onSelect(airfield)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrassConditionSelector(selected: GrassCondition, onSelect: (GrassCondition) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.flight_context_grass_condition_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
            GrassCondition.entries.forEachIndexed { index, condition ->
                SegmentedButton(
                    selected = selected == condition,
                    onClick = { onSelect(condition) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = GrassCondition.entries.size),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        when (condition) {
                            GrassCondition.DRY -> stringResource(R.string.flight_context_grass_dry)
                            GrassCondition.WET -> stringResource(R.string.flight_context_grass_wet)
                            GrassCondition.SOFT -> stringResource(R.string.flight_context_grass_soft)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RunwayAdviceList(
    advice: List<RunwayAdvice>,
    chosenDesignator: String?,
    onChoose: (String?) -> Unit
) {
    val statusColors = MaterialTheme.status
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.flight_context_runway_section_title), style = MaterialTheme.typography.labelLarge)
        if (chosenDesignator != null) {
            TextButton(onClick = { onChoose(null) }) {
                Text(stringResource(R.string.flight_context_runway_reset_to_auto))
            }
        }
        advice.forEach { item ->
            val selectable = item.status != RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED
            val isChosen = chosenDesignator == item.candidate.designator ||
                (chosenDesignator == null && item.status == RunwayAdviceStatus.PREFERRED)
            val (statusLabel, statusColor) = when (item.status) {
                RunwayAdviceStatus.PREFERRED -> stringResource(R.string.flight_context_runway_status_preferred) to MaterialTheme.colorScheme.primary
                RunwayAdviceStatus.FITS -> stringResource(R.string.flight_context_runway_status_fits) to MaterialTheme.colorScheme.onSurfaceVariant
                RunwayAdviceStatus.DOES_NOT_FIT -> stringResource(R.string.flight_context_runway_status_does_not_fit) to statusColors.warning
                RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED -> stringResource(R.string.flight_context_runway_status_tailwind) to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isChosen) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (selectable) Modifier.clickable { onChoose(item.candidate.designator) } else Modifier)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.candidate.designator, style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isChosen) Icon(Icons.Filled.Check, contentDescription = null, tint = statusColor)
                            Text(statusLabel, color = statusColor)
                        }
                    }
                    if (item.status != RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED) {
                        Text(
                            stringResource(
                                R.string.flight_context_runway_detail_format,
                                item.headwindKts.roundToInt(), item.crosswindKts.roundToInt()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        item.remainingM?.let { remaining ->
                            Text(
                                stringResource(R.string.flight_context_runway_remaining_format, remaining.roundToInt()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.crosswindExceeded) {
                            Text(
                                stringResource(R.string.flight_context_crosswind_exceeded_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColors.warning
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightContextConfirmRow(confirmed: Boolean, onConfirm: () -> Unit) {
    if (confirmed) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.flight_context_confirmed_label), color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.flight_context_confirm_button))
        }
    }
}
