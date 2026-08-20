package nl.schellenberg.hk36ttc.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.schellenberg.hk36ttc.R
import nl.schellenberg.hk36ttc.core.units.AirspeedUnit
import nl.schellenberg.hk36ttc.core.units.CgPositionUnit
import nl.schellenberg.hk36ttc.core.units.DistanceUnit
import nl.schellenberg.hk36ttc.core.units.FuelVolumeUnit
import nl.schellenberg.hk36ttc.core.units.HeightUnit
import nl.schellenberg.hk36ttc.core.units.MassUnit
import nl.schellenberg.hk36ttc.core.units.PressureUnit
import nl.schellenberg.hk36ttc.core.units.TemperatureUnit
import nl.schellenberg.hk36ttc.core.units.VerticalSpeedUnit
import nl.schellenberg.hk36ttc.core.units.WindSpeedUnit
import nl.schellenberg.hk36ttc.data.local.UnitPreferences
import nl.schellenberg.hk36ttc.ui.common.uniformSegmentedRowHeight

/**
 * One label plus an N-way segmented choice — the same compact pattern
 * [nl.schellenberg.hk36ttc.ui.common.FlightContextCard]'s grass-condition selector already
 * uses, reused here instead of a full-height Card-per-option list (as the language picker above
 * this section uses): ten quantity families at that row height wouldn't fit a phone screen
 * without heavy scrolling.
 */
@Composable
private fun <T> UnitToggleRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().uniformSegmentedRowHeight()) {
            options.forEachIndexed { index, (value, optionLabel) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(optionLabel)
                }
            }
        }
    }
}

/**
 * The "Eenheden" section of [SettingsScreen] — one [UnitToggleRow] per family in
 * [nl.schellenberg.hk36ttc.core.units.AppUnits]. Reads and writes [unitPreferences] directly
 * rather than through a ViewModel: there's no calculation to coordinate here, just a thin,
 * immediately-visible settings toggle — the same shape [SettingsScreen] already uses for the
 * language picker just above this section.
 */
@Composable
fun UnitSettingsSection(unitPreferences: UnitPreferences) {
    val units by unitPreferences.units.collectAsState()

    Text(stringResource(R.string.settings_units_heading), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(R.string.settings_units_explanation),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_temperature_label),
        options = listOf(
            TemperatureUnit.CELSIUS to stringResource(R.string.settings_unit_celsius),
            TemperatureUnit.FAHRENHEIT to stringResource(R.string.settings_unit_fahrenheit)
        ),
        selected = units.temperature,
        onSelect = { value -> unitPreferences.update { it.copy(temperature = value) } }
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_distance_label),
        options = listOf(
            DistanceUnit.METERS to stringResource(R.string.settings_unit_meters),
            DistanceUnit.FEET to stringResource(R.string.settings_unit_feet)
        ),
        selected = units.distance,
        onSelect = { value -> unitPreferences.update { it.copy(distance = value) } }
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_height_label),
        options = listOf(
            HeightUnit.METERS to stringResource(R.string.settings_unit_meters),
            HeightUnit.FEET to stringResource(R.string.settings_unit_feet)
        ),
        selected = units.height,
        onSelect = { value -> unitPreferences.update { it.copy(height = value) } }
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_wind_speed_label),
        options = listOf(
            WindSpeedUnit.METERS_PER_SECOND to stringResource(R.string.settings_unit_meters_per_second),
            WindSpeedUnit.KNOTS to stringResource(R.string.settings_unit_knots)
        ),
        selected = units.windSpeed,
        onSelect = { value -> unitPreferences.update { it.copy(windSpeed = value) } }
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_pressure_label),
        options = listOf(
            PressureUnit.HPA to stringResource(R.string.settings_unit_hpa),
            PressureUnit.INHG to stringResource(R.string.settings_unit_inhg)
        ),
        selected = units.pressure,
        onSelect = { value -> unitPreferences.update { it.copy(pressure = value) } }
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_vertical_speed_label),
        options = listOf(
            VerticalSpeedUnit.METERS_PER_SECOND to stringResource(R.string.settings_unit_meters_per_second),
            VerticalSpeedUnit.FEET_PER_MINUTE to stringResource(R.string.settings_unit_feet_per_minute)
        ),
        selected = units.verticalSpeed,
        onSelect = { value -> unitPreferences.update { it.copy(verticalSpeed = value) } }
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_airspeed_label),
        options = listOf(
            AirspeedUnit.KMH to stringResource(R.string.settings_unit_kmh),
            AirspeedUnit.KNOTS to stringResource(R.string.settings_unit_knots),
            AirspeedUnit.MPH to stringResource(R.string.settings_unit_mph)
        ),
        selected = units.airspeed,
        onSelect = { value -> unitPreferences.update { it.copy(airspeed = value) } }
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_mass_label),
        options = listOf(
            MassUnit.KG to stringResource(R.string.settings_unit_kg),
            MassUnit.LBS to stringResource(R.string.settings_unit_lbs)
        ),
        selected = units.mass,
        onSelect = { value -> unitPreferences.update { it.copy(mass = value) } }
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_cg_position_label),
        options = listOf(
            CgPositionUnit.MM to stringResource(R.string.settings_unit_mm),
            CgPositionUnit.INCH to stringResource(R.string.settings_unit_inch)
        ),
        selected = units.cgPosition,
        onSelect = { value -> unitPreferences.update { it.copy(cgPosition = value) } }
    )

    UnitToggleRow(
        label = stringResource(R.string.settings_unit_fuel_volume_label),
        options = listOf(
            FuelVolumeUnit.LITERS to stringResource(R.string.settings_unit_liters),
            FuelVolumeUnit.US_GALLONS to stringResource(R.string.settings_unit_us_gallons)
        ),
        selected = units.fuelVolume,
        onSelect = { value -> unitPreferences.update { it.copy(fuelVolume = value) } }
    )
}
