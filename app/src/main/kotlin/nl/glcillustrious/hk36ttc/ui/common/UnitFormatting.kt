package nl.glcillustrious.hk36ttc.ui.common

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import nl.glcillustrious.hk36ttc.core.metar.kmhToKts
import nl.glcillustrious.hk36ttc.core.units.AirspeedUnit
import nl.glcillustrious.hk36ttc.core.units.CgPositionUnit
import nl.glcillustrious.hk36ttc.core.units.DistanceUnit
import nl.glcillustrious.hk36ttc.core.units.FuelVolumeUnit
import nl.glcillustrious.hk36ttc.core.units.HeightUnit
import nl.glcillustrious.hk36ttc.core.units.MassUnit
import nl.glcillustrious.hk36ttc.core.units.PressureUnit
import nl.glcillustrious.hk36ttc.core.units.TemperatureUnit
import nl.glcillustrious.hk36ttc.core.units.UnitConversions
import nl.glcillustrious.hk36ttc.core.units.VerticalSpeedUnit
import nl.glcillustrious.hk36ttc.core.units.WindSpeedUnit

/**
 * Converts a native-unit value (always what a ViewModel stores and every calculation runs on —
 * see [nl.glcillustrious.hk36ttc.core.units.AppUnits]'s KDoc) to and from the pilot's chosen
 * display unit, one section per quantity family used anywhere in the app.
 *
 * Every `displayXxx` function returns a whole number, never a decimal — every converted figure
 * in the app is shown as an integer, matching how the steppers already worked before conversion
 * existed. The rounding *direction* is not always "nearest": feet is rounded down and mph is
 * rounded up, both so a converted number never reads as more favourable than the underlying
 * metric figure — a feet-displayed margin that rounds up would overstate how much runway is
 * left, and an mph Vy that rounds down would understate the safe reference speed to fly. Every
 * other quantity rounds to nearest. See [roundForDisplay].
 *
 * Only the display step rounds this way — converting the pilot's *input* back to the native
 * unit ([nativeMassKg] and friends) always rounds to nearest, since interpreting what they typed
 * has no "favourable direction" to protect against.
 *
 * The stored native value is never overwritten by a read-only display conversion: a pilot who
 * merely looks at a field in a different unit and never touches it keeps their exact stored
 * figure, no drift. Only actually changing a stepper's value writes a freshly-rounded native
 * figure back — the same one-way "confirm on interaction" rule
 * [nl.glcillustrious.hk36ttc.ui.airfield.AirfieldEditScreen]'s elevation field already follows.
 *
 * Every conversion here is a pure ratio (no zero-offset the way Celsius/Fahrenheit has), so
 * converting a *difference* (a margin, a violation's shortfall) with the same function used for
 * an absolute value is always exact — unlike temperature, nothing here needs a separate
 * delta-only conversion.
 */

private enum class RoundMode { NEAREST, DOWN, UP }

private fun roundForDisplay(value: Double, mode: RoundMode): Int = when (mode) {
    RoundMode.NEAREST -> value.roundToInt()
    RoundMode.DOWN -> floor(value).toInt()
    RoundMode.UP -> ceil(value).toInt()
}

// --- Mass (pilot/copilot/baggage inputs, aircraft empty mass/MTOW, W&B results) -------------

fun massSuffix(unit: MassUnit): String = when (unit) {
    MassUnit.KG -> "kg"
    MassUnit.LBS -> "lbs"
}

private fun rawMass(nativeKg: Double, unit: MassUnit): Double = when (unit) {
    MassUnit.KG -> nativeKg
    MassUnit.LBS -> UnitConversions.kgToLbs(nativeKg)
}

fun displayMass(nativeKg: Double, unit: MassUnit): Int = roundForDisplay(rawMass(nativeKg, unit), RoundMode.NEAREST)
fun displayMass(nativeKg: Int, unit: MassUnit): Int = displayMass(nativeKg.toDouble(), unit)

fun nativeMassKg(displayValue: Double, unit: MassUnit): Double = when (unit) {
    MassUnit.KG -> displayValue
    MassUnit.LBS -> UnitConversions.lbsToKg(displayValue)
}
fun nativeMassKgInt(displayValue: Int, unit: MassUnit): Int = nativeMassKg(displayValue.toDouble(), unit).roundToInt()

// --- CG position (W&B computed CG, envelope forward/aft limits) ----------------------------

fun cgPositionSuffix(unit: CgPositionUnit): String = when (unit) {
    CgPositionUnit.MM -> "mm"
    CgPositionUnit.INCH -> "in"
}

private fun rawCgPosition(nativeMm: Double, unit: CgPositionUnit): Double = when (unit) {
    CgPositionUnit.MM -> nativeMm
    CgPositionUnit.INCH -> UnitConversions.mmToInches(nativeMm)
}

fun displayCgPosition(nativeMm: Double, unit: CgPositionUnit): Int = roundForDisplay(rawCgPosition(nativeMm, unit), RoundMode.NEAREST)
fun displayCgPosition(nativeMm: Int, unit: CgPositionUnit): Int = displayCgPosition(nativeMm.toDouble(), unit)

fun nativeCgPositionMm(displayValue: Double, unit: CgPositionUnit): Double = when (unit) {
    CgPositionUnit.MM -> displayValue
    CgPositionUnit.INCH -> UnitConversions.inchesToMm(displayValue)
}
fun nativeCgPositionMmInt(displayValue: Int, unit: CgPositionUnit): Int = nativeCgPositionMm(displayValue.toDouble(), unit).roundToInt()

// --- Fuel volume (W&B fuel input) -----------------------------------------------------------

fun fuelVolumeSuffix(unit: FuelVolumeUnit): String = when (unit) {
    FuelVolumeUnit.LITERS -> "L"
    FuelVolumeUnit.US_GALLONS -> "gal"
}

private fun rawFuelVolume(nativeLiters: Double, unit: FuelVolumeUnit): Double = when (unit) {
    FuelVolumeUnit.LITERS -> nativeLiters
    FuelVolumeUnit.US_GALLONS -> UnitConversions.litersToUsGallons(nativeLiters)
}

fun displayFuelVolume(nativeLiters: Double, unit: FuelVolumeUnit): Int = roundForDisplay(rawFuelVolume(nativeLiters, unit), RoundMode.NEAREST)
fun displayFuelVolume(nativeLiters: Int, unit: FuelVolumeUnit): Int = displayFuelVolume(nativeLiters.toDouble(), unit)

fun nativeFuelVolumeLiters(displayValue: Double, unit: FuelVolumeUnit): Double = when (unit) {
    FuelVolumeUnit.LITERS -> displayValue
    FuelVolumeUnit.US_GALLONS -> UnitConversions.usGallonsToLiters(displayValue)
}
fun nativeFuelVolumeLitersInt(displayValue: Int, unit: FuelVolumeUnit): Int = nativeFuelVolumeLiters(displayValue.toDouble(), unit).roundToInt()

/** kg-per-litre fuel density (an AFM/POH-published constant, never pilot-entered) expressed in
 * the pilot's chosen mass-per-volume units — e.g. "kg/L" becomes "lbs/gal" when both settings
 * are imperial. Used only for the informational fuel-mass hint on the W&B screen. Rounded to
 * nearest like every other non-feet/mph quantity. */
fun displayFuelDensity(nativeKgPerLiter: Double, massUnit: MassUnit, volumeUnit: FuelVolumeUnit): Int {
    val massPerLiter = rawMass(nativeKgPerLiter, massUnit)
    val oneVolumeInLiters = nativeFuelVolumeLiters(1.0, volumeUnit)
    return roundForDisplay(massPerLiter * oneVolumeInLiters, RoundMode.NEAREST)
}

// --- Temperature (OAT input/display, METAR temperature) -------------------------------------
//
// Unlike every quantity above, Celsius<->Fahrenheit has a zero-offset (°F = °C×9/5 + 32), not a
// pure ratio. That makes converting a *difference* wrong if done with these same functions — a
// 5°C rise is a 9°F rise, not (5×9/5+32)°F. Nothing in this app currently displays a temperature
// delta (OAT is always an absolute reading), so no separate delta-only function exists yet; add
// one deliberately, don't reuse [displayTemperature], if that ever changes.

fun temperatureSuffix(unit: TemperatureUnit): String = when (unit) {
    TemperatureUnit.CELSIUS -> "°C"
    TemperatureUnit.FAHRENHEIT -> "°F"
}

private fun rawTemperature(nativeCelsius: Double, unit: TemperatureUnit): Double = when (unit) {
    TemperatureUnit.CELSIUS -> nativeCelsius
    TemperatureUnit.FAHRENHEIT -> UnitConversions.celsiusToFahrenheit(nativeCelsius)
}

fun displayTemperature(nativeCelsius: Double, unit: TemperatureUnit): Int =
    roundForDisplay(rawTemperature(nativeCelsius, unit), RoundMode.NEAREST)
fun displayTemperature(nativeCelsius: Int, unit: TemperatureUnit): Int = displayTemperature(nativeCelsius.toDouble(), unit)

fun nativeTemperatureCelsius(displayValue: Double, unit: TemperatureUnit): Double = when (unit) {
    TemperatureUnit.CELSIUS -> displayValue
    TemperatureUnit.FAHRENHEIT -> UnitConversions.fahrenheitToCelsius(displayValue)
}
fun nativeTemperatureCelsiusInt(displayValue: Int, unit: TemperatureUnit): Int =
    nativeTemperatureCelsius(displayValue.toDouble(), unit).roundToInt()

// --- Distance (runway length, ground run, obstacle distance, remaining runway) --------------
//
// Feet rounds *down*: a remaining-runway or obstacle-clearance figure must never look more
// generous in feet than the metres it was computed from.

fun distanceSuffix(unit: DistanceUnit): String = when (unit) {
    DistanceUnit.METERS -> "m"
    DistanceUnit.FEET -> "ft"
}

private fun rawDistance(nativeMeters: Double, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.METERS -> nativeMeters
    DistanceUnit.FEET -> UnitConversions.metersToFeet(nativeMeters)
}

private fun distanceRoundMode(unit: DistanceUnit): RoundMode = when (unit) {
    DistanceUnit.METERS -> RoundMode.NEAREST
    DistanceUnit.FEET -> RoundMode.DOWN
}

fun displayDistance(nativeMeters: Double, unit: DistanceUnit): Int =
    roundForDisplay(rawDistance(nativeMeters, unit), distanceRoundMode(unit))
fun displayDistance(nativeMeters: Int, unit: DistanceUnit): Int = displayDistance(nativeMeters.toDouble(), unit)

fun nativeDistanceMeters(displayValue: Double, unit: DistanceUnit): Double = when (unit) {
    DistanceUnit.METERS -> displayValue
    DistanceUnit.FEET -> UnitConversions.feetToMeters(displayValue)
}
fun nativeDistanceMetersInt(displayValue: Int, unit: DistanceUnit): Int = nativeDistanceMeters(displayValue.toDouble(), unit).roundToInt()

// --- Height (field elevation, pressure altitude) ---------------------------------------------
//
// Feet rounds *down*, same reasoning as distance: a pressure altitude or field elevation must
// never read lower (i.e. more favourable for performance) in feet than the metres it came from.

fun heightSuffix(unit: HeightUnit): String = when (unit) {
    HeightUnit.METERS -> "m"
    HeightUnit.FEET -> "ft"
}

private fun rawHeight(nativeMeters: Double, unit: HeightUnit): Double = when (unit) {
    HeightUnit.METERS -> nativeMeters
    HeightUnit.FEET -> UnitConversions.metersToFeet(nativeMeters)
}

private fun heightRoundMode(unit: HeightUnit): RoundMode = when (unit) {
    HeightUnit.METERS -> RoundMode.NEAREST
    HeightUnit.FEET -> RoundMode.DOWN
}

fun displayHeight(nativeMeters: Double, unit: HeightUnit): Int =
    roundForDisplay(rawHeight(nativeMeters, unit), heightRoundMode(unit))
fun displayHeight(nativeMeters: Int, unit: HeightUnit): Int = displayHeight(nativeMeters.toDouble(), unit)

fun nativeHeightMeters(displayValue: Double, unit: HeightUnit): Double = when (unit) {
    HeightUnit.METERS -> displayValue
    HeightUnit.FEET -> UnitConversions.feetToMeters(displayValue)
}
fun nativeHeightMetersInt(displayValue: Int, unit: HeightUnit): Int = nativeHeightMeters(displayValue.toDouble(), unit).roundToInt()

// --- Wind speed (steady + gust, headwind/crosswind components) ------------------------------

fun windSpeedSuffix(unit: WindSpeedUnit): String = when (unit) {
    WindSpeedUnit.KNOTS -> "kt"
    WindSpeedUnit.METERS_PER_SECOND -> "m/s"
}

private fun rawWindSpeed(nativeKnots: Double, unit: WindSpeedUnit): Double = when (unit) {
    WindSpeedUnit.KNOTS -> nativeKnots
    WindSpeedUnit.METERS_PER_SECOND -> UnitConversions.knotsToMetersPerSecond(nativeKnots)
}

fun displayWindSpeed(nativeKnots: Double, unit: WindSpeedUnit): Int = roundForDisplay(rawWindSpeed(nativeKnots, unit), RoundMode.NEAREST)
fun displayWindSpeed(nativeKnots: Int, unit: WindSpeedUnit): Int = displayWindSpeed(nativeKnots.toDouble(), unit)

fun nativeWindSpeedKnots(displayValue: Double, unit: WindSpeedUnit): Double = when (unit) {
    WindSpeedUnit.KNOTS -> displayValue
    WindSpeedUnit.METERS_PER_SECOND -> UnitConversions.metersPerSecondToKnots(displayValue)
}
fun nativeWindSpeedKnotsInt(displayValue: Int, unit: WindSpeedUnit): Int = nativeWindSpeedKnots(displayValue.toDouble(), unit).roundToInt()

// --- Pressure (METAR QNH) ---------------------------------------------------------------------

fun pressureSuffix(unit: PressureUnit): String = when (unit) {
    PressureUnit.HPA -> "hPa"
    PressureUnit.INHG -> "inHg"
}

private fun rawPressure(nativeHpa: Double, unit: PressureUnit): Double = when (unit) {
    PressureUnit.HPA -> nativeHpa
    PressureUnit.INHG -> UnitConversions.hPaToInHg(nativeHpa)
}

fun displayPressure(nativeHpa: Double, unit: PressureUnit): Int = roundForDisplay(rawPressure(nativeHpa, unit), RoundMode.NEAREST)

// --- Vertical speed (tow climb-rate table) ----------------------------------------------------

fun verticalSpeedSuffix(unit: VerticalSpeedUnit): String = when (unit) {
    VerticalSpeedUnit.METERS_PER_SECOND -> "m/s"
    VerticalSpeedUnit.FEET_PER_MINUTE -> "ft/min"
}

private fun rawVerticalSpeed(nativeMetersPerSecond: Double, unit: VerticalSpeedUnit): Double = when (unit) {
    VerticalSpeedUnit.METERS_PER_SECOND -> nativeMetersPerSecond
    VerticalSpeedUnit.FEET_PER_MINUTE -> UnitConversions.metersPerSecondToFeetPerMinute(nativeMetersPerSecond)
}

fun displayVerticalSpeed(nativeMetersPerSecond: Double, unit: VerticalSpeedUnit): Int =
    roundForDisplay(rawVerticalSpeed(nativeMetersPerSecond, unit), RoundMode.NEAREST)

// --- Airspeed (Vy — deliberately its own family, never tied to wind speed's kt/m-s choice) --
//
// mph rounds *up*: Vy is a target speed to fly, and rounding a reference speed down would
// quietly narrow the margin above stall it's meant to protect.

fun airspeedSuffix(unit: AirspeedUnit): String = when (unit) {
    AirspeedUnit.KMH -> "km/h"
    AirspeedUnit.KNOTS -> "kt"
    AirspeedUnit.MPH -> "mph"
}

/** [nativeKmh] is Vy as the AFM publishes it — km/h. Reuses [kmhToKts] rather than a second
 * km/h<->kt factor. */
private fun rawAirspeed(nativeKmh: Double, unit: AirspeedUnit): Double = when (unit) {
    AirspeedUnit.KMH -> nativeKmh
    AirspeedUnit.KNOTS -> kmhToKts(nativeKmh)
    AirspeedUnit.MPH -> UnitConversions.kmhToMph(nativeKmh)
}

private fun airspeedRoundMode(unit: AirspeedUnit): RoundMode = when (unit) {
    AirspeedUnit.KMH, AirspeedUnit.KNOTS -> RoundMode.NEAREST
    AirspeedUnit.MPH -> RoundMode.UP
}

fun displayAirspeed(nativeKmh: Double, unit: AirspeedUnit): Int = roundForDisplay(rawAirspeed(nativeKmh, unit), airspeedRoundMode(unit))
