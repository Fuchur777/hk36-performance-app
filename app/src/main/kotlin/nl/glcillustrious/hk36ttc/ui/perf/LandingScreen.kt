package nl.glcillustrious.hk36ttc.ui.perf

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.core.metar.MetarParser
import nl.glcillustrious.hk36ttc.core.perf.LandingResult
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.ui.common.FlightContextCard
import nl.glcillustrious.hk36ttc.ui.common.GrassConditionSelector
import nl.glcillustrious.hk36ttc.ui.common.IntStepperField
import nl.glcillustrious.hk36ttc.ui.common.MetarSummary
import nl.glcillustrious.hk36ttc.ui.common.ResettableIntStepperField
import nl.glcillustrious.hk36ttc.ui.common.ResultRow
import nl.glcillustrious.hk36ttc.ui.common.RunwayResultCard
import nl.glcillustrious.hk36ttc.ui.common.WeatherInputMode
import nl.glcillustrious.hk36ttc.ui.common.WeatherModeSelector
import nl.glcillustrious.hk36ttc.ui.common.runwayStatusPresentation
import nl.glcillustrious.hk36ttc.ui.common.uniformSegmentedRowHeight
import nl.glcillustrious.hk36ttc.ui.theme.status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingScreen(
    repository: AircraftProfileRepository,
    performanceNormal: PerformanceNormalData,
    performanceCorrections: PerformanceCorrectionsData,
    metarConfig: MetarConfigData,
    profileId: Long,
    onBack: () -> Unit
) {
    val viewModel: LandingViewModel = viewModel(
        factory = LandingViewModel.factory(repository, profileId, performanceNormal, performanceCorrections)
    )
    val state by viewModel.state.collectAsState()
    val airfields by viewModel.airfields.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.registration?.let { stringResource(R.string.landing_title_format, it) }
                            ?: stringResource(R.string.landing_title_default)
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
            FlightContextCard(
                mode = state.flightContextMode,
                onModeChange = { viewModel.setFlightContextMode(it) },
                airfields = airfields,
                selectedAirfield = state.selectedAirfield,
                onSelectAirfield = { viewModel.selectAirfield(it) }
            )

            val parsedMetar = state.selectedAirfield?.metarRaw?.let { MetarParser.parse(it) }
            if (state.weatherDerivable) {
                WeatherModeSelector(mode = state.weatherMode, onModeChange = { viewModel.setWeatherMode(it) })
                if (state.weatherMode == WeatherInputMode.METAR) {
                    MetarSummary(parsedMetar, state.selectedAirfield?.elevationM?.roundToInt() ?: 0, metarConfig)
                }
            }
            val metarWeather = state.weatherDerivable && state.weatherMode == WeatherInputMode.METAR

            if (!state.showRunwayResults) {
                if (!metarWeather) {
                    IntStepperField(
                        label = stringResource(R.string.perf_oat_label), value = state.oatC,
                        onValueChange = { v -> viewModel.update { it.copy(oatC = v) } },
                        min = -20, max = 45, suffix = "°C"
                    )
                }
                if (!(metarWeather && state.pressureAltDerivable)) {
                    IntStepperField(
                        label = stringResource(R.string.perf_pressure_alt_label), value = state.pressureAltM,
                        onValueChange = { v -> viewModel.update { it.copy(pressureAltM = v) } },
                        min = 0, max = 1500, suffix = "m"
                    )
                }
                LandingSurfaceSelector(
                    surfaceType = state.surfaceType,
                    onSelected = { type -> viewModel.update { it.copy(surfaceType = type) } }
                )
                if (state.surfaceType == LandingSurfaceType.AANGEPAST) {
                    Text(
                        stringResource(R.string.landing_custom_surface_explanation_format, viewModel.maxCustomSurfaceFactorPct()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IntStepperField(
                        label = stringResource(R.string.landing_custom_surface_factor_label), value = state.customSurfaceFactorPct,
                        onValueChange = { v -> viewModel.update { it.copy(customSurfaceFactorPct = v) } },
                        min = 100, max = viewModel.maxCustomSurfaceFactorPct(), suffix = "%"
                    )
                }
                IntStepperField(
                    label = stringResource(R.string.perf_slope_label), value = state.slopePct,
                    onValueChange = { v -> viewModel.update { it.copy(slopePct = v) } },
                    min = -10, max = 10, suffix = "%"
                )
            }

            ResettableIntStepperField(
                label = stringResource(R.string.perf_margin_factor_label), value = state.marginFactorPct,
                defaultValue = viewModel.marginFactorDefaultPct,
                onValueChange = { v -> viewModel.update { it.copy(marginFactorPct = v) } },
                min = 100, max = 200, suffix = "%"
            )
            Text(
                stringResource(R.string.landing_easa_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.hasGrassRunway) {
                GrassConditionSelector(state.grassCondition, { viewModel.setGrassCondition(it) })
            }

            if (state.showRunwayResults) {
                Text(stringResource(R.string.flight_context_runway_section_title), style = MaterialTheme.typography.labelLarge)
                state.runwayResults.forEach { row ->
                    val presentation = runwayStatusPresentation(row.advice.status)
                    RunwayResultCard(
                        label = row.advice.candidate.label,
                        statusLabel = presentation.label,
                        containerColor = presentation.containerColor,
                        contentColor = presentation.contentColor,
                        headwindKts = row.advice.headwindKts,
                        crosswindKts = row.advice.crosswindKts,
                        crosswindExceeded = row.advice.crosswindExceeded,
                        groundRunWithMarginM = row.fullResult?.l1WithMarginM,
                        groundRunRawM = row.fullResult?.l1M,
                        obstacleWithMarginM = row.fullResult?.l2WithMarginM,
                        obstacleRawM = row.fullResult?.l2M,
                        remainingM = row.advice.remainingWithMarginM,
                        surfaceLabel = landingSurfaceLabel(row.surfaceType)
                    )
                }
            } else {
                state.result?.let { LandingResultCard(it, state.surfaceType) }
            }

            Text(
                stringResource(R.string.landing_mtow_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun landingSurfaceLabel(type: LandingSurfaceType): String = when (type) {
    LandingSurfaceType.ASFALT -> stringResource(R.string.perf_surface_asfalt)
    LandingSurfaceType.DROOG_GRAS -> stringResource(R.string.perf_surface_droog_gras)
    LandingSurfaceType.NAT_GRAS -> stringResource(R.string.perf_surface_nat_gras)
    LandingSurfaceType.AANGEPAST -> stringResource(R.string.landing_surface_aangepast)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandingSurfaceSelector(surfaceType: LandingSurfaceType, onSelected: (LandingSurfaceType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.perf_surface_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
            SegmentedButton(
                selected = surfaceType == LandingSurfaceType.ASFALT,
                onClick = { onSelected(LandingSurfaceType.ASFALT) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_asfalt)) }
            SegmentedButton(
                selected = surfaceType == LandingSurfaceType.DROOG_GRAS,
                onClick = { onSelected(LandingSurfaceType.DROOG_GRAS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_droog_gras)) }
            SegmentedButton(
                selected = surfaceType == LandingSurfaceType.NAT_GRAS,
                onClick = { onSelected(LandingSurfaceType.NAT_GRAS) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_nat_gras)) }
            SegmentedButton(
                selected = surfaceType == LandingSurfaceType.AANGEPAST,
                onClick = { onSelected(LandingSurfaceType.AANGEPAST) },
                shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.landing_surface_aangepast)) }
        }
        if (surfaceType != LandingSurfaceType.ASFALT) {
            Text(
                stringResource(R.string.landing_surface_grass_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LandingResultCard(result: LandingResult, surfaceType: LandingSurfaceType) {
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
            Text(
                stringResource(R.string.perf_result_surface_format, landingSurfaceLabel(surfaceType)),
                style = MaterialTheme.typography.bodyMedium
            )
            ResultRow(stringResource(R.string.perf_ground_run_label), fmt(result.l1WithMarginM), "m")
            ResultRow(stringResource(R.string.perf_obstacle_15m_label), fmt(result.l2WithMarginM), "m")
            ResultRow(stringResource(R.string.perf_ground_run_raw_label), fmt(result.l1M), "m")
            ResultRow(stringResource(R.string.perf_obstacle_15m_raw_label), fmt(result.l2M), "m")
            if (result.surfaceFactorApplied) {
                Text(
                    stringResource(R.string.landing_surface_correction_format, fmt(result.surfaceFactor * 100)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.slopeApplied) {
                Text(
                    stringResource(R.string.landing_slope_correction_format, fmt(-result.slopePct)),
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
