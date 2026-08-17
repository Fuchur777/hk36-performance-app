package nl.glcillustrious.hk36ttc.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 */
@Composable
fun MetarSummary(
    parsed: MetarParseResult?,
    elevationM: Int,
    metarConfig: MetarConfigData,
    warning: String? = null
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
                        Text(stringResource(R.string.airfield_edit_metar_summary_pressure_altitude_format, pressureAlt.toInt()))
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
