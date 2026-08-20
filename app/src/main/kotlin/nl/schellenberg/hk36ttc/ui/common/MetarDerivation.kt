package nl.schellenberg.hk36ttc.ui.common

import kotlin.math.roundToInt
import nl.schellenberg.hk36ttc.core.metar.MetarParseResult
import nl.schellenberg.hk36ttc.core.metar.MetarParser
import nl.schellenberg.hk36ttc.core.metar.ParsedMetar
import nl.schellenberg.hk36ttc.core.metar.PressureAltitude
import nl.schellenberg.hk36ttc.data.local.AirfieldEntity

/**
 * What the selected airfield's METAR resolves to for one calculation screen.
 *
 * [oatC]/[pressureAltM] are the values the calculation should actually use — already switched
 * between the METAR's own figures and the pilot's typed ones, so a caller never has to repeat
 * that choice. [windDirectionDeg] is null when there is no usable wind at all (variable or
 * missing in the report, and nothing typed by hand yet), which is the signal that no per-runway
 * result can be produced: 0°/0 kt is a real calm wind, not a stand-in for "nothing entered".
 *
 * [windGustKts] is non-null only when the wind came from the METAR and that report carried a
 * gust group. It is deliberately *not* folded into [windSpeedKts]: the runway advice stays on
 * the steady wind, and the gust is carried alongside purely so each screen can show it in
 * brackets after the steady head- and crosswind components.
 */
data class DerivedWeather(
    val parsedMetar: ParsedMetar?,
    val oatC: Int,
    val pressureAltM: Int,
    val windDirectionDeg: Double?,
    val windSpeedKts: Double,
    val windGustKts: Double?,
    /** The METAR parsed and an airfield is selected — enough for OAT, independent of the wind. */
    val weatherDerivable: Boolean,
    /** [weatherDerivable] plus a QNH group actually being present. */
    val pressureAltDerivable: Boolean,
    /** The METAR's own wind has a specific direction (not VRB, not missing). */
    val metarWindUsable: Boolean
)

/**
 * Resolves the selected airfield's stored METAR into the inputs a calculation screen needs.
 *
 * Extracted from [nl.schellenberg.hk36ttc.ui.perf.TakeoffViewModel],
 * [nl.schellenberg.hk36ttc.ui.perf.LandingViewModel] and
 * [nl.schellenberg.hk36ttc.ui.perf.SleepvluchtViewModel], which carried three byte-identical
 * copies of this block. Three copies meant every change to the derivation — the gust handling
 * that added [DerivedWeather.windGustKts], for one — was a three-site edit that could be applied
 * inconsistently. Takes plain values rather than a screen's state object precisely so all three
 * can share it despite having separate state classes.
 *
 * The manual values ([manualOatC] and friends) are what the pilot typed on that screen; they win
 * whenever [manualWeather] is set or the METAR can't supply the figure in question.
 */
fun deriveWeather(
    selectedAirfield: AirfieldEntity?,
    airfieldContextActive: Boolean,
    manualWeather: Boolean,
    manualOatC: Int,
    manualPressureAltM: Int,
    manualWindDirectionDeg: Int,
    manualWindSpeedKts: Int,
    windManuallySet: Boolean
): DerivedWeather {
    val parsedMetar = selectedAirfield?.metarRaw
        ?.let { raw -> (MetarParser.parse(raw) as? MetarParseResult.Success)?.metar }

    val active = airfieldContextActive && selectedAirfield != null
    // OAT/pressure-alt derivability does not depend on the wind group being usable —
    // temperature and QNH parse independently of whether the wind is VRB or missing.
    val weatherDerivable = active && parsedMetar != null
    val pressureAltDerivable = weatherDerivable && parsedMetar.qnhHpa != null
    val metarWindDirection = parsedMetar?.windDirectionDeg
    val metarWindUsable = active && parsedMetar != null && metarWindDirection != null

    val oatC = if (!weatherDerivable || manualWeather) manualOatC else parsedMetar.temperatureC.roundToInt()
    // A high QNH puts the pressure altitude below sea level, but the AFM's tables start at 0 m —
    // extrapolating below that would trip the out-of-range warning for what is in fact the
    // *easiest* case (denser air, shorter distances). Clamping to 0 keeps the figures on the
    // conservative side and leaves the warning for genuine extrapolation. The METAR card still
    // shows the true derived value; only the calculation floors it.
    val pressureAltM = if (!pressureAltDerivable || manualWeather) {
        manualPressureAltM
    } else {
        PressureAltitude.fromElevationAndQnh(selectedAirfield.elevationM, parsedMetar.qnhHpa!!)
            .roundToInt()
            .coerceAtLeast(0)
    }

    // The pilot's own typed direction+speed either when they chose Handmatig weather, or when
    // they're still in METAR mode but the report's wind isn't usable (variable/missing) — either
    // way null until they've actually touched those fields, since 0°/0 kt is a real calm wind.
    val useManualWind = active && (manualWeather || !metarWindUsable)
    val windDirectionDeg = if (useManualWind) {
        if (windManuallySet) manualWindDirectionDeg.toDouble() else null
    } else {
        metarWindDirection
    }
    val windSpeedKts = if (useManualWind) manualWindSpeedKts.toDouble() else parsedMetar?.windSpeedKts ?: 0.0
    // A hand-typed wind has no gust figure to report; only a METAR-sourced one can.
    val windGustKts = if (useManualWind) null else parsedMetar?.windGustKts

    return DerivedWeather(
        parsedMetar = parsedMetar,
        oatC = oatC,
        pressureAltM = pressureAltM,
        windDirectionDeg = windDirectionDeg,
        windSpeedKts = windSpeedKts,
        windGustKts = windGustKts,
        weatherDerivable = weatherDerivable,
        pressureAltDerivable = pressureAltDerivable,
        metarWindUsable = metarWindUsable
    )
}
