package nl.glcillustrious.hk36ttc.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import nl.glcillustrious.hk36ttc.core.units.AirspeedUnit
import nl.glcillustrious.hk36ttc.core.units.AppUnits
import nl.glcillustrious.hk36ttc.core.units.CgPositionUnit
import nl.glcillustrious.hk36ttc.core.units.DistanceUnit
import nl.glcillustrious.hk36ttc.core.units.FuelVolumeUnit
import nl.glcillustrious.hk36ttc.core.units.HeightUnit
import nl.glcillustrious.hk36ttc.core.units.MassUnit
import nl.glcillustrious.hk36ttc.core.units.PressureUnit
import nl.glcillustrious.hk36ttc.core.units.TemperatureUnit
import nl.glcillustrious.hk36ttc.core.units.VerticalSpeedUnit
import nl.glcillustrious.hk36ttc.core.units.WindSpeedUnit

/**
 * Persists [AppUnits] and exposes it as a live [StateFlow], so a change in Settings is visible
 * on every open screen immediately — unlike [LanguagePreference], which needs an Activity
 * recreate to take effect, unit choices are pure UI-layer display concerns (see [AppUnits]'s
 * KDoc) and every consuming screen already recomposes on a `StateFlow` the normal Compose way.
 */
class UnitPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("unit_preferences", Context.MODE_PRIVATE)

    private val _units = MutableStateFlow(load())
    val units: StateFlow<AppUnits> = _units

    fun update(transform: (AppUnits) -> AppUnits) {
        val next = transform(_units.value)
        _units.value = next
        save(next)
    }

    private fun load(): AppUnits = AppUnits(
        temperature = readEnum(KEY_TEMPERATURE, TemperatureUnit.CELSIUS),
        distance = readEnum(KEY_DISTANCE, DistanceUnit.METERS),
        height = readEnum(KEY_HEIGHT, HeightUnit.METERS),
        windSpeed = readEnum(KEY_WIND_SPEED, WindSpeedUnit.KNOTS),
        pressure = readEnum(KEY_PRESSURE, PressureUnit.HPA),
        verticalSpeed = readEnum(KEY_VERTICAL_SPEED, VerticalSpeedUnit.METERS_PER_SECOND),
        airspeed = readEnum(KEY_AIRSPEED, AirspeedUnit.KMH),
        mass = readEnum(KEY_MASS, MassUnit.KG),
        cgPosition = readEnum(KEY_CG_POSITION, CgPositionUnit.MM),
        fuelVolume = readEnum(KEY_FUEL_VOLUME, FuelVolumeUnit.LITERS)
    )

    private fun save(units: AppUnits) {
        prefs.edit().apply {
            putString(KEY_TEMPERATURE, units.temperature.name)
            putString(KEY_DISTANCE, units.distance.name)
            putString(KEY_HEIGHT, units.height.name)
            putString(KEY_WIND_SPEED, units.windSpeed.name)
            putString(KEY_PRESSURE, units.pressure.name)
            putString(KEY_VERTICAL_SPEED, units.verticalSpeed.name)
            putString(KEY_AIRSPEED, units.airspeed.name)
            putString(KEY_MASS, units.mass.name)
            putString(KEY_CG_POSITION, units.cgPosition.name)
            putString(KEY_FUEL_VOLUME, units.fuelVolume.name)
        }.apply()
    }

    /** A value from an older app version, or a corrupt preference, falls back to [default]
     * rather than crashing the screen — the same tolerance every enum-in-SharedPreferences
     * read in this app already has. */
    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T =
        prefs.getString(key, null)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    private companion object {
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_DISTANCE = "distance"
        const val KEY_HEIGHT = "height"
        const val KEY_WIND_SPEED = "wind_speed"
        const val KEY_PRESSURE = "pressure"
        const val KEY_VERTICAL_SPEED = "vertical_speed"
        const val KEY_AIRSPEED = "airspeed"
        const val KEY_MASS = "mass"
        const val KEY_CG_POSITION = "cg_position"
        const val KEY_FUEL_VOLUME = "fuel_volume"
    }
}
