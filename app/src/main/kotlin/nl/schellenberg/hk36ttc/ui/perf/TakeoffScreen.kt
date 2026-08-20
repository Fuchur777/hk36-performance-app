package nl.schellenberg.hk36ttc.ui.perf

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.core.metar.MetarConfigData
import nl.schellenberg.hk36ttc.core.metar.MetarParser
import nl.schellenberg.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.schellenberg.hk36ttc.core.perf.PerformanceNormalData
import nl.schellenberg.hk36ttc.core.perf.TakeoffResult
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.FlightContextMode
import nl.schellenberg.hk36ttc.data.metar.MetarRepository
import nl.schellenberg.hk36ttc.ui.common.DistanceResultBlock
import nl.schellenberg.hk36ttc.ui.common.FlightContextCard
import nl.schellenberg.hk36ttc.ui.common.GrassConditionSelector
import nl.schellenberg.hk36ttc.ui.common.LocalAppUnits
import nl.schellenberg.hk36ttc.ui.common.airspeedSuffix
import nl.schellenberg.hk36ttc.ui.common.displayAirspeed
import nl.schellenberg.hk36ttc.ui.common.displayVerticalSpeed
import nl.schellenberg.hk36ttc.ui.common.verticalSpeedSuffix
import nl.schellenberg.hk36ttc.ui.common.distanceSuffix
import nl.schellenberg.hk36ttc.ui.common.displayDistance
import nl.schellenberg.hk36ttc.ui.common.displayHeight
import nl.schellenberg.hk36ttc.ui.common.displayTemperature
import nl.schellenberg.hk36ttc.ui.common.displayWindSpeed
import nl.schellenberg.hk36ttc.ui.common.grassConditionLabel
import nl.schellenberg.hk36ttc.ui.common.heightSuffix
import nl.schellenberg.hk36ttc.ui.common.IntStepperField
import nl.schellenberg.hk36ttc.ui.common.MetarSummary
import nl.schellenberg.hk36ttc.ui.common.nativeDistanceMetersInt
import nl.schellenberg.hk36ttc.ui.common.nativeHeightMetersInt
import nl.schellenberg.hk36ttc.ui.common.nativeTemperatureCelsiusInt
import nl.schellenberg.hk36ttc.ui.common.nativeWindSpeedKnotsInt
import nl.schellenberg.hk36ttc.ui.common.ResettableIntStepperField
import nl.schellenberg.hk36ttc.ui.common.ResultRow
import nl.schellenberg.hk36ttc.ui.common.RunwayResultCard
import nl.schellenberg.hk36ttc.ui.common.temperatureSuffix
import nl.schellenberg.hk36ttc.ui.common.WeatherInputMode
import nl.schellenberg.hk36ttc.ui.common.WeatherModeSelector
import nl.schellenberg.hk36ttc.ui.common.windSpeedSuffix
import nl.schellenberg.hk36ttc.ui.common.runwayStatusPresentation
import nl.schellenberg.hk36ttc.ui.common.uniformSegmentedRowHeight
import nl.schellenberg.hk36ttc.ui.report.PerformanceReportContext
import nl.schellenberg.hk36ttc.ui.report.PerformanceReportLabels
import nl.schellenberg.hk36ttc.ui.report.PerformanceReportResult
import nl.schellenberg.hk36ttc.ui.report.RunwayReportEntry
import nl.schellenberg.hk36ttc.ui.report.RunwayRowLabels
import nl.schellenberg.hk36ttc.ui.report.SharePdfButton
import nl.schellenberg.hk36ttc.ui.report.buildPerformanceReport
import nl.schellenberg.hk36ttc.ui.theme.status
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeoffScreen(
    repository: AircraftProfileRepository,
    metarRepository: MetarRepository,
    performanceNormal: PerformanceNormalData,
    performanceCorrections: PerformanceCorrectionsData,
    metarConfig: MetarConfigData,
    profileId: Long,
    onBack: () -> Unit
) {
    val viewModel: TakeoffViewModel = viewModel(
        factory = TakeoffViewModel.factory(
            repository, profileId, performanceNormal, performanceCorrections, metarRepository, metarConfig
        )
    )
    val state by viewModel.state.collectAsState()
    val airfields by viewModel.airfields.collectAsState()
    val units = LocalAppUnits.current

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
                    // Bold red, inside the METAR card: it's a statement about this METAR's own
                    // wind group, so it belongs with the decoded values rather than floating
                    // above the input fields.
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

            // Deliberately NOT gated on `!showRunwayResults`: these fields are the *source* of
            // the wind those results are computed from, so hiding them once results exist made
            // them erase themselves the moment a wind was entered. They stay put and the
            // results appear below them.
            if (state.windNeedsManualEntry) {
                // Airfield known: OAT/pressure-alt stay METAR-derived whenever the METAR
                // itself parses (they don't depend on the wind group at all), only the
                // wind needs typing — either because Handmatig was chosen, or because the
                // METAR's own wind is unusable (variable/missing). Surface/slope always
                // come from the runway data + grass-condition selector below, never asked
                // here.
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
                // Plain Handmatig (no airfield at all), or an airfield with no runways entered
                // yet: the pre-Fase-2c single-value form, where the pilot resolves the headwind
                // component, surface and slope themselves.
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
                TakeoffSurfaceSelector(
                    surfaceType = state.surfaceType,
                    onSelected = { type -> viewModel.update { it.copy(surfaceType = type) } }
                )
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

            if (state.showGrassCondition) {
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
                        headwindGustKts = row.advice.headwindGustKts,
                        crosswindGustKts = row.advice.crosswindGustKts,
                        crosswindExceeded = row.advice.crosswindExceeded,
                        groundRunWithMarginM = row.fullResult?.s1WithMarginM,
                        groundRunRawM = row.fullResult?.s1M,
                        obstacleWithMarginM = row.fullResult?.s2WithMarginM,
                        obstacleRawM = row.fullResult?.s2M,
                        remainingM = row.advice.remainingWithMarginM,
                        surfaceLabel = takeoffSurfaceLabel(row.surfaceType),
                        marginFactor = state.marginFactorPct / 100.0
                    )
                }
            } else {
                state.result?.let { TakeoffResultCard(it, state.surfaceType) }
            }

            ClimbReferenceCard(
                vyKmh = performanceNormal.climb.vyKmh,
                maxRateOfClimbMs = performanceNormal.climb.maxRateOfClimbMs,
                serviceCeilingM = performanceNormal.climb.serviceCeilingM
            )

            val reportTitle = stringResource(
                R.string.report_title_format,
                stringResource(R.string.report_title_takeoff),
                state.registration ?: ""
            )
            val labels = performanceReportLabels()
            val grassLabel = if (state.showGrassCondition) grassConditionLabel(state.grassCondition) else null
            val runwayEntries = state.runwayResults.map { row ->
                RunwayReportEntry(
                    label = row.advice.candidate.label,
                    statusLabel = runwayStatusPresentation(row.advice.status).label,
                    surfaceLabel = takeoffSurfaceLabel(row.surfaceType),
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
            // Resolved here, not inside buildDocument: that lambda runs on tap, outside any
            // composable context, so every @Composable label lookup has to be hoisted.
            val manualSurfaceLabel = takeoffSurfaceLabel(state.surfaceType)

            SharePdfButton(
                kind = "takeoff",
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
                            // In airfield mode the headwind is per runway (it's in the runway
                            // rows), so repeating a single figure here would be meaningless.
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
                                tailwindBlocked = it.tailwindBlocked
                            )
                        },
                        runways = runwayEntries,
                        units = units
                    )
                }
            )
        }
    }
}

/** Resolves every string the performance reports need, here where `stringResource` works, so
 * [buildPerformanceReport] stays a pure function. Shared by Take-off, Landing and Sleepvlucht. */
@Composable
internal fun performanceReportLabels() = PerformanceReportLabels(
    registration = stringResource(R.string.report_registration_label),
    sectionInput = stringResource(R.string.report_section_input),
    sectionWeather = stringResource(R.string.report_section_weather),
    sectionResult = stringResource(R.string.report_section_result),
    sectionRunways = stringResource(R.string.report_section_runways),
    sectionNotes = stringResource(R.string.report_section_notes),
    airfield = stringResource(R.string.report_airfield_label),
    manualMode = stringResource(R.string.report_mode_manual),
    metar = stringResource(R.string.report_metar_label),
    grassCondition = stringResource(R.string.report_grass_condition_label),
    oat = stringResource(R.string.perf_oat_label),
    pressureAlt = stringResource(R.string.perf_pressure_alt_label),
    headwind = stringResource(R.string.perf_headwind_label),
    windDirection = stringResource(R.string.perf_wind_direction_label),
    windSpeed = stringResource(R.string.perf_wind_speed_label),
    surface = stringResource(R.string.perf_surface_label),
    slope = stringResource(R.string.perf_slope_label),
    marginFactor = stringResource(R.string.perf_margin_factor_label),
    groundRun = stringResource(R.string.perf_ground_run_label),
    obstacle = stringResource(R.string.perf_obstacle_15m_label),
    groundRunRaw = stringResource(R.string.perf_ground_run_raw_label),
    obstacleRaw = stringResource(R.string.perf_obstacle_15m_raw_label),
    outOfRangeWarning = stringResource(R.string.perf_out_of_range_warning),
    tailwindNotSupported = stringResource(R.string.perf_tailwind_not_supported),
    runwayRows = RunwayRowLabels(
        surface = stringResource(R.string.perf_surface_label),
        headwind = stringResource(R.string.perf_headwind_label),
        crosswind = stringResource(R.string.report_crosswind_label),
        crosswindExceeded = stringResource(R.string.report_crosswind_exceeded),
        groundRun = stringResource(R.string.perf_ground_run_label),
        obstacle = stringResource(R.string.perf_obstacle_15m_label),
        remaining = stringResource(R.string.flight_context_runway_remaining_label),
        notCalculable = stringResource(R.string.report_runway_not_calculable)
    ),
    footer = stringResource(R.string.report_footer)
)

@Composable
internal fun takeoffSurfaceLabel(type: TakeoffSurfaceType): String = when (type) {
    TakeoffSurfaceType.ASFALT -> stringResource(R.string.perf_surface_asfalt)
    TakeoffSurfaceType.DROOG_GRAS -> stringResource(R.string.perf_surface_droog_gras)
    TakeoffSurfaceType.NAT_GRAS -> stringResource(R.string.perf_surface_nat_gras)
    TakeoffSurfaceType.ZACHTE_GROND -> stringResource(R.string.perf_surface_zacht)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TakeoffSurfaceSelector(surfaceType: TakeoffSurfaceType, onSelected: (TakeoffSurfaceType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.perf_surface_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
            SegmentedButton(
                selected = surfaceType == TakeoffSurfaceType.ASFALT,
                onClick = { onSelected(TakeoffSurfaceType.ASFALT) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_asfalt)) }
            SegmentedButton(
                selected = surfaceType == TakeoffSurfaceType.DROOG_GRAS,
                onClick = { onSelected(TakeoffSurfaceType.DROOG_GRAS) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_droog_gras)) }
            SegmentedButton(
                selected = surfaceType == TakeoffSurfaceType.NAT_GRAS,
                onClick = { onSelected(TakeoffSurfaceType.NAT_GRAS) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 4),
                modifier = Modifier.fillMaxHeight()
            ) { Text(stringResource(R.string.perf_surface_nat_gras)) }
            SegmentedButton(
                selected = surfaceType == TakeoffSurfaceType.ZACHTE_GROND,
                onClick = { onSelected(TakeoffSurfaceType.ZACHTE_GROND) },
                shape = SegmentedButtonDefaults.itemShape(index = 3, count = 4),
                modifier = Modifier.fillMaxHeight()
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
    val units = LocalAppUnits.current
    val statusColors = MaterialTheme.status
    Card(
        // Same green as a RECOMMENDED per-runway card: in Handmatig there is only one result
        // and no ranking to express, so it reads as the plain "this is your answer" card.
        colors = CardDefaults.cardColors(
            containerColor = statusColors.success,
            contentColor = statusColors.onSuccess
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.perf_result_section_title),
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                stringResource(R.string.perf_result_surface_format, takeoffSurfaceLabel(surfaceType)),
                style = MaterialTheme.typography.bodyMedium
            )
            DistanceResultBlock(
                marginFactor = result.marginFactor,
                groundRunLabel = stringResource(R.string.perf_ground_run_label),
                obstacleLabel = stringResource(R.string.perf_obstacle_15m_label),
                withMarginHeading = stringResource(R.string.perf_with_margin_heading_format, fmt(result.marginFactor)),
                withoutMarginHeading = stringResource(R.string.perf_without_margin_heading),
                groundRunWithMarginM = displayDistance(result.s1WithMarginM, units.distance),
                obstacleWithMarginM = displayDistance(result.s2WithMarginM, units.distance),
                groundRunRawM = displayDistance(result.s1M, units.distance),
                obstacleRawM = displayDistance(result.s2M, units.distance),
                unitSuffix = distanceSuffix(units.distance)
            )
            if (result.surfaceFactorApplied) {
                val tag = if (surfaceType == TakeoffSurfaceType.DROOG_GRAS) "[AFM]" else "[AIC P173]"
                Text(
                    stringResource(R.string.takeoff_surface_toeslag_format, tag, fmt((result.surfaceFactor - 1.0) * 100)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (result.slopeApplied) {
                Text(
                    stringResource(R.string.takeoff_slope_correction_format, fmt(result.slopePct)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (result.outOfRangeWarning) {
                Text(
                    stringResource(R.string.perf_out_of_range_warning),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
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
    val units = LocalAppUnits.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.takeoff_climb_heading), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(
                    R.string.takeoff_climb_vy_format,
                    displayAirspeed(vyKmh, units.airspeed).toString(),
                    displayVerticalSpeed(maxRateOfClimbMs, units.verticalSpeed).toString(),
                    airspeedSuffix(units.airspeed),
                    verticalSpeedSuffix(units.verticalSpeed)
                )
            )
            Text(
                stringResource(R.string.perf_no_correction_table_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun fmt(value: Double): String = "%.1f".format(value)
