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
import nl.schellenberg.hk36ttc.core.wb.FuelTankType
import nl.schellenberg.hk36ttc.core.wb.WbConstantsData
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.ui.common.IntStepperField
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
                value = state.emptyMassKg,
                onValueChange = { v -> viewModel.update { it.copy(emptyMassKg = v) } },
                min = 400,
                max = 700,
                suffix = "kg"
            )

            IntStepperField(
                label = stringResource(R.string.profile_edit_mtow_label),
                value = state.mtowKg,
                onValueChange = { v -> viewModel.update { it.copy(mtowKg = v) } },
                min = 600,
                max = 800,
                suffix = "kg"
            )

            // ...dan de mm-velden bij elkaar
            IntStepperField(
                label = stringResource(R.string.profile_edit_empty_mass_cg_label),
                value = state.emptyMassCgPositionMm,
                onValueChange = { v -> viewModel.update { it.copy(emptyMassCgPositionMm = v) } },
                min = 300,
                max = 500,
                suffix = "mm"
            )

            IntStepperField(
                label = stringResource(R.string.profile_edit_cg_forward_label),
                value = state.cgEnvelopeForwardLimitMm,
                onValueChange = { v -> viewModel.update { it.copy(cgEnvelopeForwardLimitMm = v) } },
                min = 300,
                max = 450,
                suffix = "mm"
            )

            IntStepperField(
                label = stringResource(R.string.profile_edit_cg_aft_label),
                value = state.cgEnvelopeAftLimitMm,
                onValueChange = { v -> viewModel.update { it.copy(cgEnvelopeAftLimitMm = v) } },
                min = 350,
                max = 500,
                suffix = "mm",
                isError = state.errors.containsKey("cgEnvelopeAftLimitMm"),
                supportingText = state.errors["cgEnvelopeAftLimitMm"]?.let { stringResource(it.toStringRes()) }
            )

            FuelTankSelector(
                selected = state.fuelTankType,
                onSelected = { v -> viewModel.update { it.copy(fuelTankType = v) } }
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
private fun FuelTankSelector(selected: FuelTankType, onSelected: (FuelTankType) -> Unit) {
    val options = FuelTankType.entries
    val label: @Composable (FuelTankType) -> String = { type ->
        when (type) {
            FuelTankType.STANDARD_55L -> stringResource(R.string.profile_edit_fuel_tank_standard)
            FuelTankType.LONG_RANGE_79L -> stringResource(R.string.profile_edit_fuel_tank_long_range)
        }
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
