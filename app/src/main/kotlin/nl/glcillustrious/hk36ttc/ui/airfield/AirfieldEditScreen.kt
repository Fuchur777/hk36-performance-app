package nl.glcillustrious.hk36ttc.ui.airfield

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.RunwaySurfaceType
import nl.glcillustrious.hk36ttc.ui.common.IntStepperField
import nl.glcillustrious.hk36ttc.ui.common.MetarSummary
import nl.glcillustrious.hk36ttc.ui.common.uniformSegmentedRowHeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirfieldEditScreen(
    repository: AircraftProfileRepository,
    metarConfig: MetarConfigData,
    airfieldId: Long,
    onBack: () -> Unit
) {
    val viewModel: AirfieldEditViewModel = viewModel(factory = AirfieldEditViewModel.factory(repository, airfieldId))
    val state by viewModel.state.collectAsState()
    val runways by viewModel.runways.collectAsState()
    var runwayDialogFor by remember { mutableStateOf<RunwayStripFormState?>(null) }
    var runwayPendingDelete by remember { mutableStateOf<RunwayStripEntity?>(null) }
    var showNewRunwayDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.id == 0L) stringResource(R.string.airfield_edit_title_new)
                        else stringResource(R.string.airfield_edit_title_existing)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.airfield_edit_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.icao,
                onValueChange = { v -> viewModel.update { it.copy(icao = v) } },
                label = { Text(stringResource(R.string.airfield_edit_icao_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.metarStationIcao,
                onValueChange = { v -> viewModel.update { it.copy(metarStationIcao = v) } },
                label = { Text(stringResource(R.string.airfield_edit_metar_station_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            IntStepperField(
                label = stringResource(R.string.airfield_edit_elevation_label),
                value = state.elevationM,
                onValueChange = { v -> viewModel.update { it.copy(elevationM = v) } },
                min = -50,
                max = 3000,
                suffix = "m"
            )

            HorizontalDivider()
            Text(stringResource(R.string.airfield_edit_metar_section_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.metarRaw,
                onValueChange = { v -> viewModel.update { it.copy(metarRaw = v) } },
                label = { Text(stringResource(R.string.airfield_edit_metar_label)) },
                placeholder = { Text(stringResource(R.string.airfield_edit_metar_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            MetarSummary(state.parsedMetar, state.elevationM, metarConfig)

            Button(
                onClick = { viewModel.saveAirfieldInfo() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.airfield_edit_save))
            }

            HorizontalDivider()
            Text(stringResource(R.string.airfield_edit_runways_section_title), style = MaterialTheme.typography.titleMedium)

            if (state.id == 0L) {
                Text(
                    stringResource(R.string.airfield_edit_runways_save_first_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                if (runways.isEmpty()) {
                    Text(
                        stringResource(R.string.airfield_edit_runways_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                runways.forEach { strip ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    if (strip.oneWay) strip.designatorA
                                    else stringResource(R.string.airfield_edit_runway_item_format, strip.designatorA, strip.designatorB)
                                )
                            },
                            supportingContent = {
                                val surfaceLabel = when (RunwaySurfaceType.valueOf(strip.surface)) {
                                    RunwaySurfaceType.ASPHALT -> stringResource(R.string.airfield_edit_runway_surface_asphalt)
                                    RunwaySurfaceType.GRASS -> stringResource(R.string.airfield_edit_runway_surface_grass)
                                }
                                Text(
                                    stringResource(R.string.airfield_edit_runway_item_detail_format, strip.lengthM.toInt(), surfaceLabel),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { runwayDialogFor = strip.toFormState() }) {
                                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.airfield_edit_runway_edit_content_description))
                                    }
                                    IconButton(onClick = { runwayPendingDelete = strip }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.airfield_edit_runway_delete_content_description),
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showNewRunwayDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.airfield_edit_add_runway))
                }
            }
        }
    }

    if (showNewRunwayDialog) {
        RunwayEditDialog(
            initial = RunwayStripFormState(),
            onDismiss = { showNewRunwayDialog = false },
            onConfirm = { form ->
                viewModel.saveRunway(form)
                showNewRunwayDialog = false
            }
        )
    }
    runwayDialogFor?.let { form ->
        RunwayEditDialog(
            initial = form,
            onDismiss = { runwayDialogFor = null },
            onConfirm = { updated ->
                viewModel.saveRunway(updated)
                runwayDialogFor = null
            }
        )
    }
    runwayPendingDelete?.let { strip ->
        AlertDialog(
            onDismissRequest = { runwayPendingDelete = null },
            title = { Text(stringResource(R.string.airfield_edit_runway_delete_confirm_title)) },
            text = {
                Text(
                    if (strip.oneWay) stringResource(R.string.airfield_edit_runway_delete_confirm_body_one_way_format, strip.designatorA)
                    else stringResource(R.string.airfield_edit_runway_delete_confirm_body_format, strip.designatorA, strip.designatorB)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRunway(strip)
                    runwayPendingDelete = null
                }) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { runwayPendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunwayEditDialog(
    initial: RunwayStripFormState,
    onDismiss: () -> Unit,
    onConfirm: (RunwayStripFormState) -> Unit
) {
    var form by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial.id == 0L) stringResource(R.string.airfield_edit_runway_dialog_title_new)
                else stringResource(R.string.airfield_edit_runway_dialog_title_existing)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { form = form.copy(oneWay = !form.oneWay) }
                ) {
                    Checkbox(checked = form.oneWay, onCheckedChange = { form = form.copy(oneWay = it) })
                    Text(stringResource(R.string.airfield_edit_runway_one_way_label))
                }
                Text(
                    stringResource(R.string.airfield_edit_runway_naming_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (form.oneWay) {
                    OutlinedTextField(
                        value = form.designatorA,
                        onValueChange = { form = form.copy(designatorA = it) },
                        label = { Text(stringResource(R.string.airfield_edit_runway_designator_one_way_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = form.designatorA,
                            onValueChange = { form = form.copy(designatorA = it) },
                            label = { Text(stringResource(R.string.airfield_edit_runway_designator_a_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = form.designatorB,
                            onValueChange = { form = form.copy(designatorB = it) },
                            label = { Text(stringResource(R.string.airfield_edit_runway_designator_b_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                IntStepperField(
                    label = stringResource(R.string.airfield_edit_runway_heading_label),
                    value = form.headingDegTrueA,
                    onValueChange = { form = form.copy(headingDegTrueA = it) },
                    min = 0,
                    max = 359,
                    suffix = "°",
                    helperText = stringResource(R.string.airfield_edit_runway_heading_hint)
                )
                IntStepperField(
                    label = stringResource(R.string.airfield_edit_runway_length_label),
                    value = form.lengthM,
                    onValueChange = { form = form.copy(lengthM = it) },
                    min = 100,
                    max = 3000,
                    suffix = "m",
                    step = 10
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.airfield_edit_runway_surface_label), style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
                        RunwaySurfaceType.entries.forEachIndexed { index, surface ->
                            SegmentedButton(
                                selected = form.surface == surface,
                                onClick = { form = form.copy(surface = surface) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = RunwaySurfaceType.entries.size),
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text(
                                    when (surface) {
                                        RunwaySurfaceType.ASPHALT -> stringResource(R.string.airfield_edit_runway_surface_asphalt)
                                        RunwaySurfaceType.GRASS -> stringResource(R.string.airfield_edit_runway_surface_grass)
                                    }
                                )
                            }
                        }
                    }
                }
                IntStepperField(
                    label = stringResource(R.string.airfield_edit_runway_slope_label),
                    value = form.slopePctA,
                    onValueChange = { form = form.copy(slopePctA = it) },
                    min = -15,
                    max = 15,
                    suffix = "%"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(form) },
                enabled = form.designatorA.isNotBlank() && (form.oneWay || form.designatorB.isNotBlank())
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
