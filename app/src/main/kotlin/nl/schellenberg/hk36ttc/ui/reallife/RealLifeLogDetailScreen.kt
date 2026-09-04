package nl.schellenberg.hk36ttc.ui.reallife

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.core.metar.MetarConfigData
import nl.schellenberg.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.schellenberg.hk36ttc.core.perf.PerformanceNormalData
import nl.schellenberg.hk36ttc.core.perf.TakeoffResult
import nl.schellenberg.hk36ttc.core.reallife.AltitudeDetectionReason
import nl.schellenberg.hk36ttc.core.reallife.ReallifeDetectionConfigData
import nl.schellenberg.hk36ttc.core.reallife.RollStartDetectionReason
import nl.schellenberg.hk36ttc.core.reallife.TakeoffDetectionResult
import nl.schellenberg.hk36ttc.core.reallife.TakeoffDistanceResult
import nl.schellenberg.hk36ttc.core.units.AppUnits
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.AirfieldEntity
import nl.schellenberg.hk36ttc.data.local.ConditionsSource
import nl.schellenberg.hk36ttc.data.local.RealLifeConfiguration
import nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeMarkerType
import nl.schellenberg.hk36ttc.data.metar.HistoricalMetarRepository
import nl.schellenberg.hk36ttc.data.metar.MetarRepository
import nl.schellenberg.hk36ttc.ui.common.LocalAppUnits
import nl.schellenberg.hk36ttc.ui.common.displayDistance
import nl.schellenberg.hk36ttc.ui.common.displayHeight
import nl.schellenberg.hk36ttc.ui.common.displayTemperature
import nl.schellenberg.hk36ttc.ui.common.displayWindSpeed
import nl.schellenberg.hk36ttc.ui.common.distanceSuffix
import nl.schellenberg.hk36ttc.ui.common.heightSuffix
import nl.schellenberg.hk36ttc.ui.common.temperatureSuffix
import nl.schellenberg.hk36ttc.ui.common.uniformSegmentedRowHeight
import nl.schellenberg.hk36ttc.ui.common.windSpeedSuffix
import nl.schellenberg.hk36ttc.ui.perf.TakeoffSurfaceType
import nl.schellenberg.hk36ttc.ui.perf.takeoffSurfaceLabel
import nl.schellenberg.hk36ttc.ui.theme.status

/** Detail screen for one recording: read-only summary, Fase 4b's detected events, and (Fase 4c)
 * the editable flight-conditions snapshot plus the handboek-vs-gemeten comparison it enables.
 * Every logical group renders as its own [SectionCard] so the screen reads as distinct blocks
 * rather than one long stack of text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealLifeLogDetailScreen(
    repository: AircraftProfileRepository,
    profileId: Long,
    logId: Long,
    performanceNormal: PerformanceNormalData,
    performanceCorrections: PerformanceCorrectionsData,
    metarRepository: MetarRepository,
    historicalMetarRepository: HistoricalMetarRepository,
    metarConfig: MetarConfigData,
    reallifeDetectionConfig: ReallifeDetectionConfigData,
    onBack: () -> Unit
) {
    val viewModel: RealLifeLogDetailViewModel = viewModel(
        factory = RealLifeLogDetailViewModel.factory(
            repository, profileId, logId, performanceNormal, performanceCorrections,
            metarRepository, historicalMetarRepository, metarConfig, reallifeDetectionConfig
        )
    )
    val state by viewModel.state.collectAsState()
    val units = LocalAppUnits.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reallife_detail_title)) },
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
        state.log?.let { current ->
            val configurationLabel = when (RealLifeConfiguration.valueOf(current.configuration)) {
                RealLifeConfiguration.NORMAL -> stringResource(R.string.reallife_configuration_normal)
                RealLifeConfiguration.SLEEPVLUCHT -> stringResource(R.string.hub_action_sleepvlucht)
            }
            val startedText = remember(current.startedAtEpochMs) {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .format(Instant.ofEpochMilli(current.startedAtEpochMs).atZone(ZoneId.systemDefault()))
            }
            val durationSeconds = current.stoppedAtEpochMs?.let { (it - current.startedAtEpochMs) / 1000 }

            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionCard {
                    DetailRow(stringResource(R.string.reallife_detail_configuration_format, configurationLabel))
                    DetailRow(startedText)
                    DetailRow(
                        if (durationSeconds != null) {
                            stringResource(R.string.reallife_list_row_duration_format, durationSeconds / 60, durationSeconds % 60)
                        } else {
                            stringResource(R.string.reallife_list_row_incomplete)
                        }
                    )
                    if (current.notes.isNotBlank()) {
                        DetailRow(stringResource(R.string.reallife_detail_notes_format, current.notes))
                    }
                    DetailRow(
                        stringResource(R.string.reallife_recording_counts_format, state.locationCount, state.imuCount, state.barometerCount)
                    )
                }

                if (state.markers.isNotEmpty()) {
                    SectionCard(title = stringResource(R.string.reallife_marker_heading)) {
                        state.markers.forEach { marker ->
                            val label = when (RealLifeMarkerType.valueOf(marker.markerType)) {
                                RealLifeMarkerType.ROLL_START -> stringResource(R.string.reallife_marker_roll_start)
                                RealLifeMarkerType.LIFT_OFF -> stringResource(R.string.reallife_marker_lift_off)
                                RealLifeMarkerType.FIFTEEN_M -> stringResource(R.string.reallife_marker_fifteen_m)
                            }
                            val offsetSeconds = (marker.epochMs - current.startedAtEpochMs) / 1000.0
                            DetailRow(stringResource(R.string.reallife_marker_row_format, label, offsetSeconds))
                        }
                    }
                }

                state.detection?.let { result -> DetectedEventsSection(result, units) }

                if (state.isEditingConditions) {
                    ConditionsEditSection(state.form, state.airfields, viewModel, units)
                } else {
                    ConditionsSummarySection(current, units, onEdit = { viewModel.beginEditingConditions(units) })
                }

                state.comparison?.let { comparison ->
                    ComparisonCard(
                        comparison,
                        measuredGroundRollM = state.detection?.groundRollDistance?.distanceM,
                        measuredTotalM = state.detection?.totalDistance?.distanceM,
                        headwindExcluded = state.comparisonHeadwindExcluded,
                        units = units
                    )
                }

                ShareRealLifeLogButton(repository = repository, log = current, registration = state.registration)
            }
        }
    }
}

/** Generic card wrapper every section below uses, so the screen reads as distinct blocks rather
 * than one long stack of text -- previously only the conditions-edit form and comparison card
 * had this treatment; the rest was plain rows with no visual separation. */
@Composable
private fun SectionCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            content()
        }
    }
}

@Composable
private fun DetailRow(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

private enum class NoteTone { WARNING, ERROR }

/** A note that needs to stand out from plain data rows -- a low-confidence flag, a fallback in
 * use, a reason something couldn't be calculated, a hard block. Previously these rendered
 * identically to normal data ([DetailRow]), indistinguishable from ordinary facts. */
@Composable
private fun NoteRow(text: String, tone: NoteTone = NoteTone.WARNING) {
    val color = when (tone) {
        NoteTone.WARNING -> MaterialTheme.status.warning
        NoteTone.ERROR -> MaterialTheme.status.error
    }
    val icon = when (tone) {
        NoteTone.WARNING -> Icons.Filled.Warning
        NoteTone.ERROR -> Icons.Filled.Error
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Fase 4b's detected events, shown read-only alongside the manual markers — computed fresh on
 * every open, not persisted, since the detector is pure and cheap to re-run. Explicitly labelled
 * "indicatief": every timing tolerance behind this detector was derived from a 4-log sample
 * against a marker that itself has known input lag (see `docs/data/reallife-samples/README.md`),
 * not independent ground truth. */
@Composable
private fun DetectedEventsSection(result: TakeoffDetectionResult, units: AppUnits) {
    val rollStartNanos = result.rollStart.elapsedRealtimeNanos
    val liftOffNanos = result.altitudeEvents.liftOffNanos
    val fifteenMNanos = result.altitudeEvents.fifteenMNanos
    if (rollStartNanos == null && liftOffNanos == null && fifteenMNanos == null) return

    SectionCard(title = stringResource(R.string.reallife_detected_heading)) {
        val baseNanos = rollStartNanos ?: liftOffNanos ?: fifteenMNanos!!
        rollStartNanos?.let {
            DetailRow(stringResource(R.string.reallife_marker_row_format, stringResource(R.string.reallife_detected_roll_start), (it - baseNanos) / 1e9))
        }
        liftOffNanos?.let {
            DetailRow(stringResource(R.string.reallife_marker_row_format, stringResource(R.string.reallife_detected_lift_off), (it - baseNanos) / 1e9))
        }
        fifteenMNanos?.let {
            DetailRow(stringResource(R.string.reallife_marker_row_format, stringResource(R.string.reallife_detected_fifteen_m), (it - baseNanos) / 1e9))
        }
        // Both AFM-comparable distances, each labelled with the exact same terms the calculation
        // screens already use ("Grondrol"/"Over 15m obstakel") rather than AFM shorthand (S1/S2)
        // -- grondrol (ROLL_START to LIFT_OFF) is the pure wheels-on-ground measurement, arguably
        // the more directly comparable and consequential of the two; the 15m figure additionally
        // folds in climb performance.
        result.groundRollDistance.distanceM?.let {
            DetailRow(
                stringResource(
                    R.string.reallife_detected_labeled_distance_format,
                    stringResource(R.string.perf_ground_run_label), displayDistance(it, units.distance), distanceSuffix(units.distance)
                )
            )
        } ?: NoteRow(distanceUnavailableReason(result, result.groundRollDistance, result.altitudeEvents.liftOffNanos))
        result.totalDistance.distanceM?.let {
            DetailRow(
                stringResource(
                    R.string.reallife_detected_labeled_distance_format,
                    stringResource(R.string.perf_obstacle_15m_label), displayDistance(it, units.distance), distanceSuffix(units.distance)
                )
            )
        } ?: NoteRow(distanceUnavailableReason(result, result.totalDistance, result.altitudeEvents.fifteenMNanos))
        // The heading actually used for the comparison's headwind component -- computed silently
        // in the background from the GPS ground track, but always shown, never hidden.
        result.rollHeadingDegTrue?.let {
            DetailRow(stringResource(R.string.reallife_detected_heading_format, it))
        }
        if (result.altitudeEvents.usedGpsAltitudeFallback) {
            NoteRow(stringResource(R.string.reallife_detected_gps_fallback_note))
        }
        if (result.rollStart.usedFallbackToLogStart) {
            NoteRow(stringResource(R.string.reallife_detected_low_confidence_note))
        }
    }
}

/** Explains why [distance]'s `distanceM` is null, using the typed reasons [TakeoffDetector]
 * already computes, rather than leaving the pilot to guess. [endNanos] is whichever event
 * timestamp [distance] measures to (`LIFT_OFF` for [TakeoffDetectionResult.groundRollDistance],
 * `FIFTEEN_M` for [TakeoffDetectionResult.totalDistance]). Only called when `distanceM == null`. */
@Composable
private fun distanceUnavailableReason(result: TakeoffDetectionResult, distance: TakeoffDistanceResult, endNanos: Long?): String = when {
    distance.outOfRange -> stringResource(R.string.reallife_detected_reason_out_of_range)
    result.rollStart.elapsedRealtimeNanos == null &&
        RollStartDetectionReason.InsufficientLocationSamples in result.rollStart.failureReasons ->
        stringResource(R.string.reallife_detected_reason_insufficient_samples)
    result.rollStart.elapsedRealtimeNanos == null ->
        stringResource(R.string.reallife_detected_reason_no_acceleration)
    endNanos == null ->
        if (AltitudeDetectionReason.NoBarometerDataAvailable in result.altitudeEvents.reasons) {
            stringResource(R.string.reallife_detected_reason_no_barometer)
        } else {
            stringResource(R.string.reallife_detected_reason_no_threshold)
        }
    else -> stringResource(R.string.reallife_detected_reason_unknown)
}

@Composable
private fun ConditionsSummarySection(log: RealLifeLogEntity, units: AppUnits, onEdit: () -> Unit) {
    SectionCard(title = stringResource(R.string.reallife_conditions_heading)) {
        val surfaceType = log.surfaceType?.let { runCatching { TakeoffSurfaceType.valueOf(it) }.getOrNull() }
        if (surfaceType != null && log.slopePct != null && log.oatC != null && log.pressureAltM != null) {
            DetailRow(
                stringResource(
                    R.string.reallife_conditions_summary_format,
                    takeoffSurfaceLabel(surfaceType), log.slopePct,
                    displayTemperature(log.oatC, units.temperature), temperatureSuffix(units.temperature),
                    displayHeight(log.pressureAltM, units.height), heightSuffix(units.height),
                    log.windDirectionDeg ?: 0, displayWindSpeed(log.windSpeedKts ?: 0, units.windSpeed), windSpeedSuffix(units.windSpeed)
                )
            )
            log.conditionsSource?.let { source ->
                val sourceLabel = when (runCatching { ConditionsSource.valueOf(source) }.getOrNull()) {
                    ConditionsSource.METAR_LIVE -> stringResource(R.string.reallife_conditions_source_live)
                    ConditionsSource.METAR_HISTORICAL -> stringResource(R.string.reallife_conditions_source_historical)
                    else -> stringResource(R.string.reallife_conditions_source_manual)
                }
                DetailRow(stringResource(R.string.reallife_conditions_source_label_format, sourceLabel))
            }
            log.metarRaw?.let { DetailRow(stringResource(R.string.reallife_conditions_metar_used_format, it)) }
        } else {
            DetailRow(stringResource(R.string.reallife_conditions_none_yet))
        }
        OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.reallife_conditions_edit_button)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionsEditSection(
    form: ConditionsFormState,
    airfields: List<AirfieldEntity>,
    viewModel: RealLifeLogDetailViewModel,
    units: AppUnits
) {
    SectionCard(title = stringResource(R.string.reallife_conditions_heading)) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
            ConditionsSourceMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = form.sourceMode == mode,
                    onClick = { viewModel.updateSourceMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ConditionsSourceMode.entries.size),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        when (mode) {
                            ConditionsSourceMode.LIVE -> stringResource(R.string.reallife_conditions_source_live)
                            ConditionsSourceMode.HISTORICAL -> stringResource(R.string.reallife_conditions_source_historical)
                            ConditionsSourceMode.MANUAL -> stringResource(R.string.reallife_conditions_source_manual)
                        }
                    )
                }
            }
        }

        if (form.sourceMode != ConditionsSourceMode.MANUAL) {
            AirfieldPickerField(airfields, form.airfieldId, viewModel::selectAirfield)
            if (form.sourceMode == ConditionsSourceMode.HISTORICAL && form.airfieldId == null) {
                OutlinedTextField(
                    value = form.manualStationIcao,
                    onValueChange = viewModel::updateManualStation,
                    label = { Text(stringResource(R.string.reallife_conditions_manual_station_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Button(
                onClick = {
                    if (form.sourceMode == ConditionsSourceMode.LIVE) viewModel.fetchLive(units) else viewModel.fetchHistorical(units)
                },
                enabled = !form.fetchInProgress
            ) {
                if (form.fetchInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.reallife_conditions_fetch_button))
                }
            }
            form.fetchError?.let { NoteRow(it, tone = NoteTone.ERROR) }
            form.metarRaw?.let { DetailRow(stringResource(R.string.reallife_conditions_metar_used_format, it)) }
        }

        Text(stringResource(R.string.reallife_conditions_surface_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
            TakeoffSurfaceType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = form.surfaceType == type,
                    onClick = { viewModel.updateSurfaceType(type) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TakeoffSurfaceType.entries.size),
                    modifier = Modifier.fillMaxHeight()
                ) { Text(takeoffSurfaceLabel(type)) }
            }
        }

        OutlinedTextField(
            value = form.slopePct, onValueChange = viewModel::updateSlopePct,
            label = { Text(stringResource(R.string.reallife_conditions_slope_label)) }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.oatC, onValueChange = viewModel::updateOatC,
            label = {
                Text(
                    stringResource(
                        R.string.reallife_conditions_label_with_unit_format,
                        stringResource(R.string.reallife_conditions_oat_label), temperatureSuffix(units.temperature)
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.pressureAltM, onValueChange = viewModel::updatePressureAltM,
            label = {
                Text(
                    stringResource(
                        R.string.reallife_conditions_label_with_unit_format,
                        stringResource(R.string.reallife_conditions_pressure_alt_label), heightSuffix(units.height)
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.windDirectionDeg, onValueChange = viewModel::updateWindDirectionDeg,
            label = { Text(stringResource(R.string.reallife_conditions_wind_direction_label)) }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.windSpeedKts, onValueChange = viewModel::updateWindSpeedKts,
            label = {
                Text(
                    stringResource(
                        R.string.reallife_conditions_label_with_unit_format,
                        stringResource(R.string.reallife_conditions_wind_speed_label), windSpeedSuffix(units.windSpeed)
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.saveConditions(units) }) { Text(stringResource(R.string.reallife_conditions_save_button)) }
            OutlinedButton(onClick = viewModel::cancelEditingConditions) { Text(stringResource(R.string.reallife_conditions_cancel_button)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirfieldPickerField(airfields: List<AirfieldEntity>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val selectedName = airfields.firstOrNull { it.id == selectedId }?.name ?: stringResource(R.string.reallife_conditions_airfield_none)
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedName, onValueChange = {}, readOnly = true, enabled = false,
            label = { Text(stringResource(R.string.reallife_conditions_airfield_label)) },
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reallife_conditions_airfield_none)) },
                onClick = { menuExpanded = false; onSelect(null) }
            )
            airfields.forEach { airfield ->
                DropdownMenuItem(
                    text = { Text(airfield.name) },
                    onClick = { menuExpanded = false; onSelect(airfield.id) }
                )
            }
        }
    }
}

/** Handboek (AFM `s1M`/`s2M`, no margin) vs. gemeten (Fase 4b's detected distances) — only
 * rendered when both a full conditions snapshot AND a detection exist. Shows BOTH AFM figures,
 * each as a compact side-by-side "Handboek"/"Gemeten" block with the verschil underneath, rather
 * than six separate stacked rows. Runway heading has its own row in [DetectedEventsSection] --
 * showing it again here would just repeat the same fact. */
@Composable
private fun ComparisonCard(
    comparison: TakeoffResult,
    measuredGroundRollM: Double?,
    measuredTotalM: Double?,
    headwindExcluded: Boolean,
    units: AppUnits
) {
    SectionCard(title = stringResource(R.string.reallife_comparison_heading)) {
        if (comparison.tailwindBlocked) {
            NoteRow(stringResource(R.string.reallife_comparison_tailwind_warning), tone = NoteTone.ERROR)
        } else {
            DistanceComparisonBlock(stringResource(R.string.perf_ground_run_label), comparison.s1M, measuredGroundRollM, units)
            DistanceComparisonBlock(stringResource(R.string.perf_obstacle_15m_label), comparison.s2M, measuredTotalM, units)
            if (comparison.outOfRangeWarning) {
                NoteRow(stringResource(R.string.reallife_comparison_out_of_range_warning))
            }
            if (headwindExcluded) {
                NoteRow(stringResource(R.string.reallife_comparison_wind_excluded_warning))
            }
        }
    }
}

/** One AFM figure vs. its measured counterpart, as two side-by-side big numbers with a smaller
 * verschil row underneath -- replaces what used to be three separate stacked text rows per
 * distance (handboek/gemeten/verschil × two distances = six rows of plain text). The measured
 * figure and the difference are colour-coded against the AFM one -- red when the pilot actually
 * needed more runway than predicted, green when less -- since that is the one fact this whole
 * card exists to surface at a glance. */
@Composable
private fun DistanceComparisonBlock(label: String, handboekM: Double, measuredM: Double?, units: AppUnits) {
    val statusColors = MaterialTheme.status
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column {
                Text(
                    stringResource(R.string.reallife_comparison_afm_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.reallife_comparison_meters_format, displayDistance(handboekM, units.distance), distanceSuffix(units.distance)),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            measuredM?.let {
                Column {
                    Text(
                        stringResource(R.string.reallife_comparison_measured_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.reallife_comparison_meters_format, displayDistance(it, units.distance), distanceSuffix(units.distance)),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (it > handboekM) statusColors.error else if (it < handboekM) statusColors.success else Color.Unspecified
                    )
                }
            }
        }
        measuredM?.let {
            Text(
                stringResource(
                    R.string.reallife_comparison_diff_only_format,
                    displayDistance(it - handboekM, units.distance), distanceSuffix(units.distance)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (it > handboekM) statusColors.error else if (it < handboekM) statusColors.success else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
