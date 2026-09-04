package nl.schellenberg.hk36ttc.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.core.units.FuelVolumeUnit
import nl.schellenberg.hk36ttc.core.wb.FuelTankType
import nl.schellenberg.hk36ttc.core.wb.WbConstantsData
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.ui.common.IntStepperField
import nl.schellenberg.hk36ttc.ui.common.LocalAppUnits
import nl.schellenberg.hk36ttc.ui.common.cgPositionSuffix
import nl.schellenberg.hk36ttc.ui.common.displayCgPosition
import nl.schellenberg.hk36ttc.ui.common.displayFuelVolume
import nl.schellenberg.hk36ttc.ui.common.displayMass
import nl.schellenberg.hk36ttc.ui.common.fuelVolumeSuffix
import nl.schellenberg.hk36ttc.ui.common.massSuffix
import nl.schellenberg.hk36ttc.ui.common.nativeCgPositionMmInt
import nl.schellenberg.hk36ttc.ui.common.nativeMassKgInt
import nl.schellenberg.hk36ttc.ui.common.uniformSegmentedRowHeight

private fun ProfileFieldError.toStringRes(): Int = when (this) {
    ProfileFieldError.REQUIRED -> R.string.profile_edit_error_required
    ProfileFieldError.AFT_LIMIT_MUST_EXCEED_FORWARD -> R.string.profile_edit_error_aft_limit
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    repository: AircraftProfileRepository,
    wbConstants: WbConstantsData,
    profileId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: ProfileEditViewModel =
        viewModel(factory = ProfileEditViewModel.factory(repository, profileId, wbConstants))
    val state by viewModel.state.collectAsState()
    val units = LocalAppUnits.current

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (profileId == 0L) stringResource(R.string.profile_edit_title_new)
                        else stringResource(R.string.profile_edit_title_existing)
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.profile_edit_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.registration,
                onValueChange = { v -> viewModel.update { it.copy(registration = v) } },
                label = { Text(stringResource(R.string.profile_edit_registration_label)) },
                isError = state.errors.containsKey("registration"),
                supportingText = state.errors["registration"]?.let { { Text(stringResource(it.toStringRes())) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // kg-velden bij elkaar...
            IntStepperField(
                label = stringResource(R.string.profile_edit_empty_mass_label),
                value = displayMass(state.emptyMassKg, units.mass),
                onValueChange = { v -> viewModel.update { it.copy(emptyMassKg = nativeMassKgInt(v, units.mass)) } },
                min = displayMass(400, units.mass),
                max = displayMass(700, units.mass),
                suffix = massSuffix(units.mass)
            )

            IntStepperField(
                label = stringResource(R.string.profile_edit_mtow_label),
                value = displayMass(state.mtowKg, units.mass),
                onValueChange = { v -> viewModel.update { it.copy(mtowKg = nativeMassKgInt(v, units.mass)) } },
                min = displayMass(600, units.mass),
                max = displayMass(800, units.mass),
                suffix = massSuffix(units.mass)
            )

            // ...dan de mm-velden bij elkaar
            IntStepperField(
                label = stringResource(R.string.profile_edit_empty_mass_cg_label),
                value = displayCgPosition(state.emptyMassCgPositionMm, units.cgPosition),
                onValueChange = { v -> viewModel.update { it.copy(emptyMassCgPositionMm = nativeCgPositionMmInt(v, units.cgPosition)) } },
                min = displayCgPosition(300, units.cgPosition),
                max = displayCgPosition(500, units.cgPosition),
                suffix = cgPositionSuffix(units.cgPosition)
            )

            IntStepperField(
                label = stringResource(R.string.profile_edit_cg_forward_label),
                value = displayCgPosition(state.cgEnvelopeForwardLimitMm, units.cgPosition),
                onValueChange = { v -> viewModel.update { it.copy(cgEnvelopeForwardLimitMm = nativeCgPositionMmInt(v, units.cgPosition)) } },
                min = displayCgPosition(300, units.cgPosition),
                max = displayCgPosition(450, units.cgPosition),
                suffix = cgPositionSuffix(units.cgPosition)
            )

            IntStepperField(
                label = stringResource(R.string.profile_edit_cg_aft_label),
                value = displayCgPosition(state.cgEnvelopeAftLimitMm, units.cgPosition),
                onValueChange = { v -> viewModel.update { it.copy(cgEnvelopeAftLimitMm = nativeCgPositionMmInt(v, units.cgPosition)) } },
                min = displayCgPosition(350, units.cgPosition),
                max = displayCgPosition(500, units.cgPosition),
                suffix = cgPositionSuffix(units.cgPosition),
                isError = state.errors.containsKey("cgEnvelopeAftLimitMm"),
                supportingText = state.errors["cgEnvelopeAftLimitMm"]?.let { stringResource(it.toStringRes()) }
            )

            FuelTankSelector(
                selected = state.fuelTankType,
                onSelected = { v -> viewModel.update { it.copy(fuelTankType = v) } },
                wbConstants = wbConstants,
                fuelVolumeUnit = units.fuelVolume
            )

            Button(
                onClick = { viewModel.save() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.profile_edit_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelTankSelector(
    selected: FuelTankType,
    onSelected: (FuelTankType) -> Unit,
    wbConstants: WbConstantsData,
    fuelVolumeUnit: FuelVolumeUnit
) {
    val options = FuelTankType.entries
    // The two options are told apart purely by their converted capacity (55 L / 79 L become
    // ~15/21 US gal) — matching how every other quantity in this screen displays in the pilot's
    // chosen unit rather than the AFM's own liters.
    val label: @Composable (FuelTankType) -> String = { type ->
        val capacityDisplay = displayFuelVolume(wbConstants.tankCapacityLiters(type), fuelVolumeUnit)
        stringResource(R.string.profile_edit_fuel_tank_capacity_format, capacityDisplay, fuelVolumeSuffix(fuelVolumeUnit))
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.profile_edit_fuel_tank_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
            options.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == selected,
                    onClick = { onSelected(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(label(type))
                }
            }
        }
    }
}
