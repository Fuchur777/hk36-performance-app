package nl.glcillustrious.hk36ttc.core.units

/**
 * The pilot's chosen display unit for every physical quantity the app shows, one enum per
 * quantity family. `core` has no Android resources to localize a label with — same pattern as
 * every other typed choice in this module (see [nl.glcillustrious.hk36ttc.core.perf.TowBlockReason]) —
 * so the app layer maps each value to a stringResource() and a display suffix.
 *
 * Every conversion in [UnitConversions] is display-only: the AFM tables, the pressure-altitude
 * derivation, the CG-envelope check and every other calculation always run in the AFM's own
 * native units (Celsius, metres, knots, hPa, mm, kg, litres) regardless of what's selected here.
 * Only the number a pilot types or reads is converted, at the UI boundary, in both directions —
 * see rekenlogica.md for why the underlying tables can never be anything but what the AFM
 * itself publishes.
 */
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

/** Runway length, ground run, obstacle distance, remaining runway. Deliberately a *separate*
 * setting from [HeightUnit] — a pilot may want feet for altitude (a near-universal aviation
 * convention even in metric countries) while keeping runway length in metres, or vice versa. */
enum class DistanceUnit { METERS, FEET }

/** Field elevation and pressure altitude. */
enum class HeightUnit { METERS, FEET }

/** Steady and gust wind speed, headwind/crosswind components. */
enum class WindSpeedUnit { KNOTS, METERS_PER_SECOND }

/** METAR QNH. */
enum class PressureUnit { HPA, INHG }

/** The tow climb-rate table (vertical speed) — deliberately separate from [AirspeedUnit], which
 * covers Vy (a horizontal speed). */
enum class VerticalSpeedUnit { METERS_PER_SECOND, FEET_PER_MINUTE }

/** Vy specifically. Its own family rather than sharing [WindSpeedUnit], since a pilot may want
 * wind in knots and Vy in km/h (the AFM's own published unit for it) at the same time. */
enum class AirspeedUnit { KMH, KNOTS, MPH }

/** Aircraft empty mass, MTOW, pilot/copilot/baggage inputs, tow weight — every mass in the W&B
 * and tow-class modules. */
enum class MassUnit { KG, LBS }

/** CG position and the CG-envelope forward/aft limits. The violation/warning check itself
 * always compares in mm, matching the AFM's own reference datum — see [UnitConversions]'s KDoc
 * for why converting only one side of that comparison would be dangerous. */
enum class CgPositionUnit { MM, INCH }

/** The W&B fuel-quantity input. */
enum class FuelVolumeUnit { LITERS, US_GALLONS }

/**
 * Round-trip conversions for every unit family above. Each pair is exact inverses of each
 * other to within floating-point rounding — see [nl.glcillustrious.hk36ttc.core.units.UnitConversionsTest]
 * for the round-trip proof on every one of them.
 *
 * kt<->km/h reuses [nl.glcillustrious.hk36ttc.core.metar.kmhToKts] rather than redefining the
 * same 1.852 factor a second time.
 */
object UnitConversions {

    fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0
    fun fahrenheitToCelsius(fahrenheit: Double): Double = (fahrenheit - 32.0) * 5.0 / 9.0

    private const val METERS_PER_FOOT = 0.3048
    fun metersToFeet(meters: Double): Double = meters / METERS_PER_FOOT
    fun feetToMeters(feet: Double): Double = feet * METERS_PER_FOOT

    private const val METERS_PER_SECOND_PER_KNOT = 0.5144444444444444
    fun knotsToMetersPerSecond(knots: Double): Double = knots * METERS_PER_SECOND_PER_KNOT
    fun metersPerSecondToKnots(metersPerSecond: Double): Double = metersPerSecond / METERS_PER_SECOND_PER_KNOT

    private const val HPA_PER_INHG = 33.8639
    fun hPaToInHg(hPa: Double): Double = hPa / HPA_PER_INHG
    fun inHgToHPa(inHg: Double): Double = inHg * HPA_PER_INHG

    private const val FEET_PER_MINUTE_PER_METER_PER_SECOND = 196.85039370078738
    fun metersPerSecondToFeetPerMinute(metersPerSecond: Double): Double =
        metersPerSecond * FEET_PER_MINUTE_PER_METER_PER_SECOND
    fun feetPerMinuteToMetersPerSecond(feetPerMinute: Double): Double =
        feetPerMinute / FEET_PER_MINUTE_PER_METER_PER_SECOND

    private const val KM_PER_MILE = 1.609344
    fun kmhToMph(kmh: Double): Double = kmh / KM_PER_MILE
    fun mphToKmh(mph: Double): Double = mph * KM_PER_MILE

    private const val KG_PER_POUND = 0.45359237
    fun kgToLbs(kg: Double): Double = kg / KG_PER_POUND
    fun lbsToKg(lbs: Double): Double = lbs * KG_PER_POUND

    private const val MM_PER_INCH = 25.4
    fun mmToInches(mm: Double): Double = mm / MM_PER_INCH
    fun inchesToMm(inches: Double): Double = inches * MM_PER_INCH

    private const val LITERS_PER_US_GALLON = 3.785411784
    fun litersToUsGallons(liters: Double): Double = liters / LITERS_PER_US_GALLON
    fun usGallonsToLiters(gallons: Double): Double = gallons * LITERS_PER_US_GALLON
}

/**
 * The pilot's complete set of unit choices, one field per family above. Defaults match the
 * app's own pre-existing behaviour (metric/Celsius/knots/hPa), so a pilot who never opens the
 * new settings section sees no change at all.
 *
 * Deliberately plain data, with no Android dependency — [nl.glcillustrious.hk36ttc.data.local.UnitPreferences]
 * is what persists it, and the UI layer reads it live via a `CompositionLocal` rather than
 * threading it through every screen's function signature by hand, since virtually every numeric
 * field in the app is a consumer.
 */
data class AppUnits(
    val temperature: TemperatureUnit = TemperatureUnit.CELSIUS,
    val distance: DistanceUnit = DistanceUnit.METERS,
    val height: HeightUnit = HeightUnit.METERS,
    val windSpeed: WindSpeedUnit = WindSpeedUnit.KNOTS,
    val pressure: PressureUnit = PressureUnit.HPA,
    val verticalSpeed: VerticalSpeedUnit = VerticalSpeedUnit.METERS_PER_SECOND,
    val airspeed: AirspeedUnit = AirspeedUnit.KMH,
    val mass: MassUnit = MassUnit.KG,
    val cgPosition: CgPositionUnit = CgPositionUnit.MM,
    val fuelVolume: FuelVolumeUnit = FuelVolumeUnit.LITERS
)
