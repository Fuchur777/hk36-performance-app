package nl.glcillustrious.hk36ttc.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.glcillustrious.hk36ttc.R
import nl.glcillustrious.hk36ttc.core.metar.MetarAge
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.core.metar.MetarParseError
import nl.glcillustrious.hk36ttc.core.metar.MetarParseResult
import nl.glcillustrious.hk36ttc.core.metar.PressureAltitude

fun MetarParseError.toStringRes(): Int = when (this) {
    MetarParseError.Empty -> R.string.airfield_edit_metar_error_empty
    MetarParseError.MissingStation -> R.string.airfield_edit_metar_error_missing_station
    MetarParseError.MissingObservationTime -> R.string.airfield_edit_metar_error_missing_time
    MetarParseError.MissingWind -> R.string.airfield_edit_metar_error_missing_wind
    MetarParseError.MissingTemperature -> R.string.airfield_edit_metar_error_missing_temperature
}

/**
 * Renders a parsed METAR (or a specific parse error, or the "nothing pasted yet" hint) as a
 * small card: wind, temperature, QNH-derived pressure altitude, and observation age against
 * [MetarConfigData.staleAfterMinutes] (rekenlogica.md §9). Shared between the airfield editor
 * (where the METAR is entered) and [FlightContextCard] (where it's reviewed before a
 * calculation) so the two never drift apart on what's shown or how staleness is judged.
 *
 * [warning] is an optional bold-red line appended inside the card — used by the calculation
 * screens for "this METAR decoded fine, but the wind can't be used, type it yourself"
 * (rekenlogica.md §5). It belongs *in* the card rather than beside it because it's a statement
 * about this METAR's contents, not about the form below it.
 *
 * [onRefresh] adds a manual "check for a new METAR" action, shown only once the report is
 * already stale (the same [MetarConfigData.staleAfterMinutes] threshold that turns the age line
 * red) — a fresh report needs no button. Left null by the airfield editor, which already
 * refreshes on its own whenever the station changes or the screen is saved; the calculation
 * screens pass it because their own background auto-refresh only ever tries once per airfield
 * per time the screen is opened (see `TakeoffViewModel.autoRefreshMetarIfStale`), so a pilot who
 * leaves a screen open through a report's staleness window needs an explicit way to ask again.
 * [refreshing] disables the button and shows a small spinner in its place while that request is
 * in flight; the METAR card underneath stays exactly as it was throughout, since the pilot is
 * still meant to read the last-known weather while waiting on the new one.
 */
@Composable
fun MetarSummary(
    parsed: MetarParseResult?,
    elevationM: Int,
    metarConfig: MetarConfigData,
    warning: String? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false
) {
    if (parsed == null) {
        Text(
            stringResource(R.string.airfield_edit_metar_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    when (parsed) {
        is MetarParseResult.Failure -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Text(
                stringResource(parsed.reason.toStringRes()),
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }

        is MetarParseResult.Success -> {
            val metar = parsed.metar
            val units = LocalAppUnits.current
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val windGustKts = metar.windGustKts
                    val qnhHpa = metar.qnhHpa
                    val windText = when {
                        // The long variant spells out "richting onbekend, vul handmatig in"
                        // itself, which would repeat [warning] verbatim two lines further
                        // down. When the caller supplies that warning it states it more
                        // prominently, so the wind line drops back to just the facts; the
                        // airfield editor passes no warning and keeps the full sentence.
                        metar.windVariableDirection -> if (warning != null) {
                            stringResource(R.string.airfield_edit_metar_summary_wind_variable_short, metar.windSpeedKts.toInt())
                        } else {
                            stringResource(R.string.airfield_edit_metar_summary_wind_variable, metar.windSpeedKts.toInt())
                        }
                        windGustKts != null -> stringResource(
                            R.string.airfield_edit_metar_summary_wind_gust_format,
                            metar.windDirectionDeg?.toInt() ?: 0, metar.windSpeedKts.toInt(), windGustKts.toInt()
                        )
                        else -> stringResource(
                            R.string.airfield_edit_metar_summary_wind_format,
                            metar.windDirectionDeg?.toInt() ?: 0, metar.windSpeedKts.toInt()
                        )
                    }
                    Text(windText)
                    Text(stringResource(R.string.airfield_edit_metar_summary_temperature_format, metar.temperatureC.toInt()))
                    if (qnhHpa != null) {
                        Text(stringResource(R.string.airfield_edit_metar_summary_qnh_format, qnhHpa.toInt()))
                        val pressureAlt = PressureAltitude.fromElevationAndQnh(elevationM.toDouble(), qnhHpa)
                        Text(
                            stringResource(
                                R.string.airfield_edit_metar_summary_pressure_altitude_format,
                                displayHeight(pressureAlt, units.height),
                                heightSuffix(units.height)
                            )
                        )
                    } else {
                        Text(
                            stringResource(R.string.airfield_edit_metar_summary_no_qnh),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    val ageMinutes = MetarAge.minutesSince(metar)
                    val stale = ageMinutes >= metarConfig.staleAfterMinutes
                    val ageText = if (stale) {
                        stringResource(R.string.airfield_edit_metar_summary_age_stale_format, ageMinutes.toInt())
                    } else {
                        stringResource(R.string.airfield_edit_metar_summary_age_format, ageMinutes.toInt())
                    }
                    Text(
                        ageText,
                        color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (stale && onRefresh != null) {
                        if (refreshing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.airfield_edit_metar_fetching),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            TextButton(onClick = onRefresh, contentPadding = PaddingValues(0.dp)) {
                                Text(stringResource(R.string.metar_summary_refresh_action))
                            }
                        }
                    }
                    if (warning != null) {
                        Text(
                            warning,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
