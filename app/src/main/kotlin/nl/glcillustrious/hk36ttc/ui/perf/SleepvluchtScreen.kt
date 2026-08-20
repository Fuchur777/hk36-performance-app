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
import nl.glcillustrious.hk36ttc.data.metar.MetarRepository
import nl.glcillustrious.hk36ttc.core.metar.MetarParser
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceTowData
import nl.glcillustrious.hk36ttc.core.perf.SailplaneTypesData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.FlightContextMode
import nl.glcillustrious.hk36ttc.ui.common.FlightContextCard
import nl.glcillustrious.hk36ttc.ui.common.GrassConditionSelector
import nl.glcillustrious.hk36ttc.ui.common.IntStepperField
import nl.glcillustrious.hk36ttc.ui.common.LocalAppUnits
import nl.glcillustrious.hk36ttc.ui.common.MetarSummary
import nl.glcillustrious.hk36ttc.ui.common.ResettableIntStepperField
import nl.glcillustrious.hk36ttc.ui.common.RunwayResultCard
import nl.glcillustrious.hk36ttc.ui.common.WeatherInputMode
import nl.glcillustrious.hk36ttc.ui.common.WeatherModeSelector
import nl.glcillustrious.hk36ttc.ui.common.displayHeight
import nl.glcillustrious.hk36ttc.ui.common.displayMass
import nl.glcillustrious.hk36ttc.ui.common.displayTemperature
import nl.glcillustrious.hk36ttc.ui.common.displayWindSpeed
import nl.glcillustrious.hk36ttc.ui.common.grassConditionLabel
import nl.glcillustrious.hk36ttc.ui.common.heightSuffix
import nl.glcillustrious.hk36ttc.ui.common.massSuffix
import nl.glcillustrious.hk36ttc.ui.common.nativeHeightMetersInt
import nl.glcillustrious.hk36ttc.ui.common.nativeTemperatureCelsiusInt
import nl.glcillustrious.hk36ttc.ui.common.nativeWindSpeedKnotsInt
import nl.glcillustrious.hk36ttc.ui.common.runwayStatusPresentation
import nl.glcillustrious.hk36ttc.ui.common.temperatureSuffix
import nl.glcillustrious.hk36ttc.ui.common.uniformSegmentedRowHeight
import nl.glcillustrious.hk36ttc.ui.common.windSpeedSuffix
import nl.glcillustrious.hk36ttc.ui.report.PerformanceReportContext
import nl.glcillustrious.hk36ttc.ui.report.PerformanceReportResult
import nl.glcillustrious.hk36ttc.ui.report.ReportDocument
import nl.glcillustrious.hk36ttc.ui.report.RunwayReportEntry
import nl.glcillustrious.hk36ttc.ui.report.SharePdfButton
import nl.glcillustrious.hk36ttc.ui.report.buildPerformanceReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepvluchtScreen(
    repository: AircraftProfileRepository,
    metarRepository: MetarRepository,
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
            repository, profileId, performanceTow, performanceNormal, performanceCorrections, sailplaneTypes,
            metarRepository, metarConfig
        )
    )
    val state by viewModel.state.collectAsState()
    val favoriteTypes by viewModel.favoriteSailplaneTypes.collectAsState()
    val airfields by viewModel.airfields.collectAsState()
    val units = LocalAppUnits.current

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
            if (state.flightContextMode == FlightContextMode.AIRFIELD && state.selectedAirfield != null) {
                WeatherModeSelector(mode = state.weatherMode, onModeChange = { viewModel.setWeatherMode(it) })
            }
            if (state.flightContextMode == FlightContextMode.AIRFIELD && state.selectedAirfield?.metarRaw != null &&
                (!state.weatherDerivable || state.weatherMode == WeatherInputMode.METAR)
            ) {
                MetarSummary(
                    parsedMetar,
                    state.selectedAirfield?.elevationM?.roundToInt() ?: 0,
                    metarConfig,
                    warning = if (state.windDirectionUnknown) {
                        stringResource(R.string.perf_wind_direction_unknown_warning)
                    } else {
                        null
                    },
                    onRefresh = { viewModel.refreshMetarNow() },
                    refreshing = state.metarRefreshing
                )
            }
            val metarWeather = state.weatherDerivable && state.weatherMode == WeatherInputMode.METAR

            // See TakeoffScreen: not gated on `!showRunwayResults` — these fields feed those
            // results, so they must not vanish the moment a wind is entered.
            if (state.windNeedsManualEntry) {
                if (!metarWeather) {
                    IntStepperField(
                        label = stringResource(R.string.perf_oat_label),
                        value = displayTemperature(state.oatC, units.temperature),
                        onValueChange = { v -> viewModel.update { it.copy(oatC = nativeTemperatureCelsiusInt(v, units.temperature)) } },
                        min = displayTemperature(-20, units.temperature),
                        max = displayTemperature(45, units.temperature),
                        suffix = temperatureSuffix(units.temperature)
                    )
                }
                if (!(metarWeather && state.pressureAltDerivable)) {
                    IntStepperField(
                        label = stringResource(R.string.perf_pressure_alt_label),
                        value = displayHeight(state.pressureAltM, units.height),
                        onValueChange = { v -> viewModel.update { it.copy(pressureAltM = nativeHeightMetersInt(v, units.height)) } },
                        min = 0, max = displayHeight(1500, units.height), suffix = heightSuffix(units.height)
                    )
                }
                IntStepperField(
                    label = stringResource(R.string.perf_wind_direction_label), value = state.windDirectionDeg,
                    onValueChange = { v -> viewModel.update { it.copy(windDirectionDeg = v, windManuallySet = true) } },
                    min = 0, max = 359, suffix = "°"
                )
                IntStepperField(
                    label = stringResource(R.string.perf_wind_speed_label),
                    value = displayWindSpeed(state.windSpeedKts, units.windSpeed),
                    onValueChange = { v ->
                        viewModel.update { it.copy(windSpeedKts = nativeWindSpeedKnotsInt(v, units.windSpeed), windManuallySet = true) }
                    },
                    min = 0, max = displayWindSpeed(60, units.windSpeed), suffix = windSpeedSuffix(units.windSpeed)
                )
            } else if (!state.showRunwayResults) {
                if (!metarWeather) {
                    IntStepperField(
                        label = stringResource(R.string.perf_oat_label),
                        value = displayTemperature(state.oatC, units.temperature),
                        onValueChange = { v -> viewModel.update { it.copy(oatC = nativeTemperatureCelsiusInt(v, units.temperature)) } },
                        min = displayTemperature(-20, units.temperature),
                        max = displayTemperature(45, units.temperature),
                        suffix = temperatureSuffix(units.temperature)
                    )
                }
                if (!(metarWeather && state.pressureAltDerivable)) {
                    IntStepperField(
                        label = stringResource(R.string.perf_pressure_alt_label),
                        value = displayHeight(state.pressureAltM, units.height),
                        onValueChange = { v -> viewModel.update { it.copy(pressureAltM = nativeHeightMetersInt(v, units.height)) } },
                        min = 0, max = displayHeight(1500, units.height), suffix = heightSuffix(units.height)
                    )
                }
                IntStepperField(
                    label = stringResource(R.string.perf_headwind_label),
                    value = displayWindSpeed(state.headwindKts, units.windSpeed),
                    onValueChange = { v -> viewModel.update { it.copy(headwindKts = nativeWindSpeedKnotsInt(v, units.windSpeed)) } },
                    min = displayWindSpeed(-10, units.windSpeed), max = displayWindSpeed(20, units.windSpeed),
                    suffix = windSpeedSuffix(units.windSpeed)
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

            if (state.showGrassCondition) {
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
                        headwindGustKts = row.advice.headwindGustKts,
                        crosswindGustKts = row.advice.crosswindGustKts,
                        crosswindExceeded = row.advice.crosswindExceeded,
                        groundRunWithMarginM = row.fullResult?.s1WithMarginM,
                        groundRunRawM = row.fullResult?.s1M,
                        obstacleWithMarginM = row.fullResult?.s2WithMarginM,
                        obstacleRawM = row.fullResult?.s2M,
                        remainingM = row.advice.remainingWithMarginM,
                        surfaceLabel = sleepvluchtSurfaceLabel(row.surfaceType),
                        marginFactor = state.marginFactorPct / 100.0
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

            val reportTitle = stringResource(
                R.string.report_title_format,
                stringResource(R.string.report_title_sleepvlucht),
                state.registration ?: ""
            )
            val labels = performanceReportLabels()
            val grassLabel = if (state.showGrassCondition) grassConditionLabel(state.grassCondition) else null
            val runwayEntries = state.runwayResults.map { row ->
                RunwayReportEntry(
                    label = row.advice.candidate.label,
                    statusLabel = runwayStatusPresentation(row.advice.status).label,
                    surfaceLabel = sleepvluchtSurfaceLabel(row.surfaceType),
                    headwindKts = row.advice.headwindKts,
                    crosswindKts = row.advice.crosswindKts,
                    headwindGustKts = row.advice.headwindGustKts,
                    crosswindGustKts = row.advice.crosswindGustKts,
                    crosswindExceeded = row.advice.crosswindExceeded,
                    groundRunWithMarginM = row.fullResult?.s1WithMarginM,
                    obstacleWithMarginM = row.fullResult?.s2WithMarginM,
                    remainingM = row.advice.remainingWithMarginM
                )
            }
            val singleResult = state.result?.takeIf { !state.showRunwayResults }
            val manualEntry = state.windNeedsManualEntry
            // All hoisted out of buildDocument: it runs on tap, outside composable context.
            val manualSurfaceLabel = sleepvluchtSurfaceLabel(state.surfaceType)
            val sailplaneLabel = stringResource(R.string.sleepvlucht_glider_weight_label)
            val ldLabel = stringResource(R.string.sleepvlucht_ld_label)
            val instructionLabel = stringResource(R.string.sleepvlucht_instruction_flight_label)
            val towplaneLabel = stringResource(R.string.sleepvlucht_towplane_weight_label)
            val yes = stringResource(R.string.common_yes)
            val no = stringResource(R.string.common_no)
            val classNote = state.result?.selectedClassId?.let {
                stringResource(R.string.sleepvlucht_class_format, it)
            }
            // A blocked tow is exactly what a saved report should record, so the reasons come
            // along rather than the button simply being unavailable.
            val blockNotes = state.result?.blockReasons.orEmpty().map { towBlockReasonText(it) }

            SharePdfButton(
                kind = "sleepvlucht",
                registration = state.registration,
                enabled = state.showRunwayResults || singleResult != null,
                buildDocument = { timestamp ->
                    buildPerformanceReport(
                        title = reportTitle,
                        timestamp = timestamp,
                        labels = labels,
                        context = PerformanceReportContext(
                            registration = state.registration,
                            airfieldName = state.selectedAirfield?.name,
                            metarRaw = state.selectedAirfield?.metarRaw,
                            grassConditionLabel = grassLabel,
                            oatC = state.effectiveOatC,
                            pressureAltM = state.effectivePressureAltM,
                            headwindKts = if (state.showRunwayResults) null else state.headwindKts,
                            windDirectionDeg = if (manualEntry) state.windDirectionDeg else null,
                            windSpeedKts = if (manualEntry) state.windSpeedKts else null,
                            surfaceLabel = if (state.showRunwayResults) null else manualSurfaceLabel,
                            slopePct = if (state.showRunwayResults) null else state.slopePct,
                            marginFactorPct = state.marginFactorPct
                        ),
                        result = singleResult?.let {
                            PerformanceReportResult(
                                groundRunWithMarginM = it.s1WithMarginM,
                                obstacleWithMarginM = it.s2WithMarginM,
                                groundRunRawM = it.s1M,
                                obstacleRawM = it.s2M,
                                outOfRange = it.outOfRangeWarning,
                                tailwindBlocked = false
                            )
                        },
                        runways = runwayEntries,
                        units = units,
                        // The tow-specific inputs the shared context has no field for — they
                        // exist on no other screen.
                        extraInputRows = listOf(
                            ReportDocument.Row(sailplaneLabel, "${displayMass(state.sailplaneMassKg, units.mass)} ${massSuffix(units.mass)}"),
                            ReportDocument.Row(ldLabel, if (state.ldRatioKnown) "${state.ldRatio}" else "—"),
                            ReportDocument.Row(instructionLabel, if (state.instructionFlight) yes else no),
                            ReportDocument.Row(
                                towplaneLabel,
                                "${displayMass(state.effectiveTowplaneMassKg, units.mass)} ${massSuffix(units.mass)}"
                            )
                        ),
                        extraNotes = listOfNotNull(classNote) + blockNotes
                    )
                }
            )
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
