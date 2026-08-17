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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdviceStatus
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextMode
import nl.glcillustrious.hk36ttc.data.local.GrassCondition
import nl.glcillustrious.hk36ttc.ui.theme.status

/**
 * Whether OAT/wind/QNH-derived values come from the selected airfield's METAR or are typed in
 * by hand — deliberately its own toggle, separate from [FlightContextMode] (rekenlogica.md §5):
 * a pilot might trust the field/runway choice from a saved airfield but still want to type
 * today's actual temperature or wind by hand, without losing the airfield selection itself.
 * Surface/slope keep their own separate per-field override instead of joining this toggle,
 * since a runway's surface is a property of the strip, not something a METAR reports.
 */
enum class WeatherInputMode { METAR, MANUAL }

/**
 * The Vliegveld/Handmatig toggle and airfield picker — shared by the Take-off/Landing/
 * Sleepvlucht screens (rekenlogica.md §5). Deliberately stops short of the METAR summary (only
 * meaningful once [WeatherInputMode.METAR] is picked — see [WeatherModeSelector]), the grass
 * condition (see [GrassConditionSelector]) and the per-runway results (see [RunwayResultCard]):
 * Fase 2c ronde 3 dropped the single confirmed runway choice in favor of showing every runway's
 * result live, so each of these pieces needs to be positionable independently rather than
 * living in one fixed card — Sleepvlucht in particular shows the runway results at the very
 * bottom of its form, since the ranking depends on sailplane/towplane mass entered there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightContextCard(
    mode: FlightContextMode,
    onModeChange: (FlightContextMode) -> Unit,
    airfields: List<AirfieldEntity>,
    selectedAirfield: AirfieldEntity?,
    onSelectAirfield: (AirfieldEntity) -> Unit
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
                    }
                }
            }
        }
    }
}

/**
 * Segmented "METAR | Handmatig" toggle for the OAT/wind/QNH-derived fields, shown only while
 * [WeatherInputMode] is a meaningful choice (i.e. the airfield's METAR actually yields usable
 * weather — see each screen's `state.weatherDerivable`). Switching to [WeatherInputMode.MANUAL]
 * is the caller's job to seed with the current METAR-derived values before flipping the mode,
 * so editing starts from what the METAR said rather than from stale/default numbers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherModeSelector(mode: WeatherInputMode, onModeChange: (WeatherInputMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.weather_mode_label), style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
            WeatherInputMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = mode == option,
                    onClick = { onModeChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = WeatherInputMode.entries.size),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        when (option) {
                            WeatherInputMode.METAR -> stringResource(R.string.weather_mode_metar)
                            WeatherInputMode.MANUAL -> stringResource(R.string.weather_mode_manual)
                        }
                    )
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

/**
 * Today's grass condition (droog/nat/zacht) — deliberately positioned by each screen right
 * before the margin-factor setting (Fase 2c ronde 3), since it's the last input the per-runway
 * results below depend on. Only meaningful when the selected airfield actually has a grass
 * runway; callers gate rendering on that themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrassConditionSelector(selected: GrassCondition, onSelect: (GrassCondition) -> Unit) {
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

data class RunwayStatusPresentation(val label: String, val containerColor: Color, val contentColor: Color)

/** The shared 4-tier color scheme for [RunwayAdviceStatus] every screen's results list uses:
 * green when recommended, yellow when it simply fits, orange when it only fits once the
 * safety margin is dropped, red when it's impossible (including a tailwind heading the AFM
 * has no data for at all). */
@Composable
fun runwayStatusPresentation(status: RunwayAdviceStatus): RunwayStatusPresentation {
    val statusColors = MaterialTheme.status
    return when (status) {
        RunwayAdviceStatus.RECOMMENDED -> RunwayStatusPresentation(
            stringResource(R.string.flight_context_runway_status_recommended), statusColors.success, statusColors.onSuccess
        )
        RunwayAdviceStatus.FITS -> RunwayStatusPresentation(
            stringResource(R.string.flight_context_runway_status_fits), statusColors.caution, statusColors.onCaution
        )
        RunwayAdviceStatus.FITS_WITHOUT_MARGIN -> RunwayStatusPresentation(
            stringResource(R.string.flight_context_runway_status_fits_without_margin), statusColors.warning, statusColors.onWarning
        )
        RunwayAdviceStatus.DOES_NOT_FIT -> RunwayStatusPresentation(
            stringResource(R.string.flight_context_runway_status_does_not_fit), statusColors.error, statusColors.onError
        )
        RunwayAdviceStatus.TAILWIND_NOT_SUPPORTED -> RunwayStatusPresentation(
            stringResource(R.string.flight_context_runway_status_tailwind), statusColors.error, statusColors.onError
        )
    }
}

/**
 * One runway's full result, color-coded by [statusLabel]/[containerColor] — Fase 2c ronde 3
 * replaced "pick one recommended runway and confirm" with "show every runway's numbers all the
 * time, continuously recalculated" (rekenlogica.md §5), so there is no click target here at
 * all, just information. [groundRunWithMarginM]/[groundRunRawM]/[obstacleWithMarginM]/
 * [obstacleRawM]/[remainingM] are null exactly when the heading is a tailwind the AFM has no
 * data for (nothing to compute); [crosswindKts]/[headwindKts] are always available since the
 * wind component itself doesn't depend on AFM data.
 */
@Composable
fun RunwayResultCard(
    label: String,
    statusLabel: String,
    containerColor: Color,
    contentColor: Color,
    headwindKts: Double,
    crosswindKts: Double,
    crosswindExceeded: Boolean,
    groundRunWithMarginM: Double?,
    groundRunRawM: Double?,
    obstacleWithMarginM: Double?,
    obstacleRawM: Double?,
    remainingM: Double?,
    surfaceLabel: String
) {
    Card(colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(statusLabel, style = MaterialTheme.typography.labelLarge)
            }
            Text(
                stringResource(R.string.flight_context_runway_detail_format, headwindKts.roundToInt(), crosswindKts.roundToInt()),
                style = MaterialTheme.typography.bodySmall
            )
            if (crosswindExceeded) {
                Text(
                    stringResource(R.string.flight_context_crosswind_exceeded_warning),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                stringResource(R.string.perf_result_surface_format, surfaceLabel),
                style = MaterialTheme.typography.bodySmall
            )
            if (groundRunWithMarginM != null && obstacleWithMarginM != null &&
                groundRunRawM != null && obstacleRawM != null
            ) {
                ResultRow(stringResource(R.string.perf_ground_run_label), fmtDistance(groundRunWithMarginM), "m")
                ResultRow(stringResource(R.string.perf_obstacle_15m_label), fmtDistance(obstacleWithMarginM), "m")
                // Not bold: reference figures beside the two above, which are what the pilot
                // actually plans on — same treatment as the single-result cards.
                ResultRow(stringResource(R.string.perf_ground_run_raw_label), fmtDistance(groundRunRawM), "m", emphasized = false)
                ResultRow(stringResource(R.string.perf_obstacle_15m_raw_label), fmtDistance(obstacleRawM), "m", emphasized = false)
                remainingM?.let {
                    ResultRow(stringResource(R.string.flight_context_runway_remaining_label), fmtSignedDistance(it), "m")
                }
            } else {
                Text(
                    stringResource(R.string.flight_context_runway_status_tailwind),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun fmtDistance(value: Double): String = "%.1f".format(value)
private fun fmtSignedDistance(value: Double): String = "%+.1f".format(value)
