package nl.glcillustrious.hk36ttc.ui.perf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.core.perf.TakeoffResult
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.ui.common.IntStepperField
import nl.glcillustrious.hk36ttc.ui.common.ResettableIntStepperField
import nl.glcillustrious.hk36ttc.ui.common.ResultRow
import nl.glcillustrious.hk36ttc.ui.theme.status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffScreen(
    repository: AircraftProfileRepository,
    performanceNormal: PerformanceNormalData,
    performanceCorrections: PerformanceCorrectionsData,
    profileId: Long,
    onBack: () -> Unit
) {
    val viewModel: TakeoffViewModel = viewModel(
        factory = TakeoffViewModel.factory(repository, profileId, performanceNormal, performanceCorrections)
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.registration?.let { stringResource(R.string.takeoff_title_format, it) }
                            ?: stringResource(R.string.takeoff_title_default)
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
            IntStepperField(
                label = stringResource(R.string.perf_oat_label), value = state.oatC,
                onValueChange = { v -> viewModel.update { it.copy(oatC = v) } },
                min = -20, max = 45, suffix = "°C"
            )
            IntStepperField(
                label = stringResource(R.string.perf_pressure_alt_label), value = state.pressureAltM,
                onValueChange = { v -> viewModel.update { it.copy(pressureAltM = v) } },
                min = 0, max = 1500, suffix = "m"
            )
            IntStepperField(
                label = stringResource(R.string.perf_headwind_label), value = state.headwindKts,
                onValueChange = { v -> viewModel.update { it.copy(headwindKts = v) } },
                min = -10, max = 20, suffix = "kts"
            )

            TakeoffSurfaceSelector(
                surfaceType = state.surfaceType,
                onSelected = { type -> viewModel.update { it.copy(surfaceType = type) } }
            )

            IntStepperField(
                label = stringResource(R.string.perf_slope_label), value = state.slopePct,
                onValueChange = { v -> viewModel.update { it.copy(slopePct = v) } },
                min = -10, max = 10, suffix = "%"
            )

            ResettableIntStepperField(
                label = stringResource(R.string.perf_margin_factor_label), value = state.marginFactorPct,
                defaultValue = viewModel.marginFactorDefaultPct,
                onValueChange = { v -> viewModel.update { it.copy(marginFactorPct = v) } },
                min = 100, max = 200, suffix = "%"
            )

            state.result?.let { TakeoffResultCard(it, state.surfaceType) }

            ClimbReferenceCard(
                vyKmh = performanceNormal.climb.vyKmh,
                maxRateOfClimbMs = performanceNormal.climb.maxRateOfClimbMs,
                serviceCeilingM = performanceNormal.climb.serviceCeilingM
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TakeoffSurfaceSelector(surfaceType: TakeoffSurfaceType, onSelected: (TakeoffSurfaceType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.perf_surface_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = surfaceType == TakeoffSurfaceType.ASFALT,
                onClick = { onSelected(TakeoffSurfaceType.ASFALT) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4)
            ) { Text(stringResource(R.string.perf_surface_asfalt)) }
            SegmentedButton(
                selected = surfaceType == TakeoffSurfaceType.DROOG_GRAS,
                onClick = { onSelected(TakeoffSurfaceType.DROOG_GRAS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4)
            ) { Text(stringResource(R.string.perf_surface_droog_gras)) }
            SegmentedButton(
                selected = surfaceType == TakeoffSurfaceType.NAT_GRAS,
                onClick = { onSelected(TakeoffSurfaceType.NAT_GRAS) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4)
            ) { Text(stringResource(R.string.perf_surface_nat_gras)) }
            SegmentedButton(
                selected = surfaceType == TakeoffSurfaceType.ZACHTE_GROND,
                onClick = { onSelected(TakeoffSurfaceType.ZACHTE_GROND) },
                shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4)
            ) { Text(stringResource(R.string.perf_surface_zacht)) }
        }
        when (surfaceType) {
            TakeoffSurfaceType.DROOG_GRAS -> Text(
                stringResource(R.string.takeoff_surface_droog_gras_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TakeoffSurfaceType.NAT_GRAS, TakeoffSurfaceType.ZACHTE_GROND -> Text(
                stringResource(R.string.takeoff_surface_nat_zacht_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TakeoffSurfaceType.ASFALT -> {}
        }
    }
}

@Composable
private fun TakeoffResultCard(result: TakeoffResult, surfaceType: TakeoffSurfaceType) {
    if (result.tailwindBlocked) {
        TailwindBlockedCard()
        return
    }
    val statusColors = MaterialTheme.status
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.perf_margin_included_format, fmt(result.marginFactor)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ResultRow(stringResource(R.string.perf_ground_run_label), fmt(result.s1WithMarginM), "m")
            ResultRow(stringResource(R.string.perf_obstacle_15m_label), fmt(result.s2WithMarginM), "m")
            if (result.surfaceFactorApplied) {
                val tag = if (surfaceType == TakeoffSurfaceType.DROOG_GRAS) "[AFM]" else "[AIC P173]"
                Text(
                    stringResource(R.string.takeoff_surface_toeslag_format, tag, fmt((result.surfaceFactor - 1.0) * 100)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.slopeApplied) {
                Text(
                    stringResource(R.string.takeoff_slope_correction_format, fmt(result.slopePct)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.outOfRangeWarning) {
                Text(
                    stringResource(R.string.perf_out_of_range_warning),
                    color = statusColors.warning,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun TailwindBlockedCard() {
    val statusColors = MaterialTheme.status
    Card(
        colors = CardDefaults.cardColors(containerColor = statusColors.warning, contentColor = statusColors.onWarning),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.perf_warning_heading), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.perf_tailwind_not_supported),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun ClimbReferenceCard(vyKmh: Double, maxRateOfClimbMs: Double, serviceCeilingM: Double) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.takeoff_climb_heading), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(R.string.takeoff_climb_vy_format, fmt(vyKmh), fmt(maxRateOfClimbMs)))
            Text(
                stringResource(R.string.perf_no_correction_table_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun fmt(value: Double): String = "%.1f".format(value)
