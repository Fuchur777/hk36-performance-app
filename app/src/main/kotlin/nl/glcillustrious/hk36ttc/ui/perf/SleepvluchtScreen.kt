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
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceTowData
import nl.glcillustrious.hk36ttc.core.perf.SailplaneTypesData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.ui.common.FlightContextCard
import nl.glcillustrious.hk36ttc.ui.common.GrassConditionSelector
import nl.glcillustrious.hk36ttc.ui.common.IntStepperField
import nl.glcillustrious.hk36ttc.ui.common.MetarSummary
import nl.glcillustrious.hk36ttc.ui.common.ResettableIntStepperField
import nl.glcillustrious.hk36ttc.ui.common.RunwayResultCard
import nl.glcillustrious.hk36ttc.ui.common.WeatherInputMode
import nl.glcillustrious.hk36ttc.ui.common.WeatherModeSelector
import nl.glcillustrious.hk36ttc.ui.common.runwayStatusPresentation
import nl.glcillustrious.hk36ttc.ui.common.uniformSegmentedRowHeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepvluchtScreen(
    repository: AircraftProfileRepository,
    performanceTow: PerformanceTowData,
    performanceNormal: PerformanceNormalData,
    performanceCorrections: PerformanceCorrectionsData,
    sailplaneTypes: SailplaneTypesData,
    metarConfig: MetarConfigData,
    profileId: Long,
    onBack: () -> Unit
) {
    val viewModel: SleepvluchtViewModel = viewModel(
        factory = SleepvluchtViewModel.factory(
            repository, profileId, performanceTow, performanceNormal, performanceCorrections, sailplaneTypes
        )
    )
    val state by viewModel.state.collectAsState()
    val favoriteTypes by viewModel.favoriteSailplaneTypes.collectAsState()
    val airfields by viewModel.airfields.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.registration?.let { stringResource(R.string.sleepvlucht_title_format, it) }
                            ?: stringResource(R.string.sleepvlucht_title_default)
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
                IntStepperField(
                    label = stringResource(R.string.perf_headwind_label), value = state.headwindKts,
                    onValueChange = { v -> viewModel.update { it.copy(headwindKts = v) } },
                    min = -10, max = 20, suffix = "kts"
                )
                SleepSurfaceSelector(
                    surfaceType = state.surfaceType,
                    onSelected = { type -> viewModel.update { it.copy(surfaceType = type) } }
                )
                IntStepperField(
                    label = stringResource(R.string.perf_slope_label), value = state.slopePct,
                    onValueChange = { v -> viewModel.update { it.copy(slopePct = v) } },
                    min = -10, max = 10, suffix = "%"
                )
            }

            SailplaneTypeField(
                selectedTypeName = state.selectedSailplaneTypeName,
                usedFallback = state.selectedSailplaneTypeUsedFallback,
                sailplaneMassKg = state.sailplaneMassKg,
                ldRatioKnown = state.ldRatioKnown,
                ldRatio = state.ldRatio,
                favoriteTypes = favoriteTypes,
                onSelectType = { type -> viewModel.selectSailplaneType(type) },
                onEditManually = { viewModel.clearSailplaneTypeSelection() },
                onManualMassChange = { v -> viewModel.update { it.copy(sailplaneMassKg = v) } },
                onLdRatioKnownChange = { v -> viewModel.update { it.copy(ldRatioKnown = v) } },
                onManualLdChange = { v -> viewModel.update { it.copy(ldRatio = v) } }
            )

            LabeledCheckbox(
                label = stringResource(R.string.sleepvlucht_instruction_flight_label),
                checked = state.instructionFlight,
                onCheckedChange = { v -> viewModel.update { it.copy(instructionFlight = v) } }
            )

            TowplaneMassField(
                fromWbKg = state.towplaneMassFromWbKg,
                manualOverride = state.towplaneMassManualOverride,
                manualKg = state.towplaneMassManualKg,
                onEditManually = {
                    viewModel.update {
                        it.copy(
                            towplaneMassManualOverride = true,
                            towplaneMassManualKg = it.towplaneMassFromWbKg ?: it.towplaneMassManualKg
                        )
                    }
                },
                onUseWbValue = { viewModel.update { it.copy(towplaneMassManualOverride = false) } },
                onManualChange = { v -> viewModel.update { it.copy(towplaneMassManualKg = v) } }
            )

            ResettableIntStepperField(
                label = stringResource(R.string.perf_margin_factor_label), value = state.marginFactorPct,
                defaultValue = viewModel.marginFactorDefaultPct,
                onValueChange = { v -> viewModel.update { it.copy(marginFactorPct = v) } },
                min = 100, max = 200, suffix = "%"
            )

            if (state.hasGrassRunway) {
                GrassConditionSelector(state.grassCondition, { viewModel.setGrassCondition(it) })
            }

            // Ranked only here, at the bottom — the ranking needs sailplane/towplane mass,
            // which aren't known until the fields above are filled in. Continuously
            // recalculated, no confirmation gate: by the time this shows, every other input
            // already exists, so it's just showing the outcome for the configuration already
            // on screen.
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
                        groundRunWithMarginM = row.fullResult?.s1WithMarginM,
                        groundRunRawM = row.fullResult?.s1M,
                        obstacleWithMarginM = row.fullResult?.s2WithMarginM,
                        obstacleRawM = row.fullResult?.s2M,
                        remainingM = row.advice.remainingWithMarginM,
                        surfaceLabel = sleepvluchtSurfaceLabel(row.surfaceType)
                    )
                }
            } else {
                state.result?.let {
                    SleepvluchtResultCard(
                        result = it,
                        surfaceType = state.surfaceType,
                        surfaceFactor = viewModel.surfaceFactorFor(state.surfaceType)
                    )
                }
            }

            SleepClimbReferenceCard(performanceTow)
        }
    }
}

@Composable
internal fun sleepvluchtSurfaceLabel(type: SleepvluchtSurfaceType): String = when (type) {
    SleepvluchtSurfaceType.ASFALT -> stringResource(R.string.perf_surface_asfalt)
    SleepvluchtSurfaceType.DROOG_GRAS -> stringResource(R.string.perf_surface_droog_gras)
    SleepvluchtSurfaceType.NAT_GRAS -> stringResource(R.string.perf_surface_nat_gras)
    SleepvluchtSurfaceType.ZACHT -> stringResource(R.string.perf_surface_zacht)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepSurfaceSelector(surfaceType: SleepvluchtSurfaceType, onSelected: (SleepvluchtSurfaceType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.perf_surface_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
            SegmentedButton(
                selected = surfaceType == SleepvluchtSurfaceType.ASFALT,
                onClick = { onSelected(SleepvluchtSurfaceType.ASFALT) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_asfalt)) }
            SegmentedButton(
                selected = surfaceType == SleepvluchtSurfaceType.DROOG_GRAS,
                onClick = { onSelected(SleepvluchtSurfaceType.DROOG_GRAS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_droog_gras)) }
            SegmentedButton(
                selected = surfaceType == SleepvluchtSurfaceType.NAT_GRAS,
                onClick = { onSelected(SleepvluchtSurfaceType.NAT_GRAS) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_nat_gras)) }
            SegmentedButton(
                selected = surfaceType == SleepvluchtSurfaceType.ZACHT,
                onClick = { onSelected(SleepvluchtSurfaceType.ZACHT) },
                shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_zacht)) }
        }
        when (surfaceType) {
            SleepvluchtSurfaceType.DROOG_GRAS -> Text(
                stringResource(R.string.sleepvlucht_surface_droog_gras_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SleepvluchtSurfaceType.ASFALT -> Text(
                stringResource(R.string.sleepvlucht_surface_asfalt_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SleepvluchtSurfaceType.NAT_GRAS, SleepvluchtSurfaceType.ZACHT -> Text(
                stringResource(R.string.sleepvlucht_surface_nat_zacht_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
