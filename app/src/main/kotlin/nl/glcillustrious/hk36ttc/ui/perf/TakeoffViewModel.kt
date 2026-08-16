package nl.glcillustrious.hk36ttc.ui.perf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.glcillustrious.hk36ttc.core.metar.MetarParseResult
import nl.glcillustrious.hk36ttc.core.metar.MetarParser
import nl.glcillustrious.hk36ttc.core.metar.PressureAltitude
import nl.glcillustrious.hk36ttc.core.metar.RequiredDistances
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdvice
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdvisor
import nl.glcillustrious.hk36ttc.core.metar.kmhToKts
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCalculator
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.core.perf.TakeoffResult
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextMode
import nl.glcillustrious.hk36ttc.data.local.GrassCondition
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.RunwaySurfaceType
import nl.glcillustrious.hk36ttc.data.local.TakeoffInputEntity
import nl.glcillustrious.hk36ttc.ui.common.LoadGuard
import nl.glcillustrious.hk36ttc.ui.common.RunwayDirectionOption
import nl.glcillustrious.hk36ttc.ui.common.WeatherInputMode
import nl.glcillustrious.hk36ttc.ui.common.directionOptions
import nl.glcillustrious.hk36ttc.ui.common.toCandidate

/**
 * Dry grass keeps the AFM's own minimum penalty (§5.3.3, confirmed by AIC P173 §5) — wet
 * grass and soft ground have no AFM figure at all for take-off and come from AIC P173 §5
 * only. Unlike landing's "Aangepast", AIC P173 gives precise figures for all three take-off
 * surface categories, so there's no need for a user-entered custom value here.
 */
enum class TakeoffSurfaceType { ASFALT, DROOG_GRAS, NAT_GRAS, ZACHTE_GROND }

/**
 * One runway direction's full take-off result — [RunwayAdvice] alone only carries the
 * distance-to-15m-obstacle (s2, the figure that decides fit/doesn't-fit), since that's the
 * one figure [nl.glcillustrious.hk36ttc.core.metar.RunwayAdvisor] itself needs; the ground-roll
 * (s1) and the surface actually used are display-only extras the ViewModel attaches here.
 * [fullResult] is null exactly when [advice]'s heading is a tailwind the AFM has no data for.
 */
data class TakeoffRunwayResult(
    val advice: RunwayAdvice,
    val surfaceType: TakeoffSurfaceType,
    val fullResult: TakeoffResult?
)

/**
 * Whole-number inputs matching the stepper UI. [marginFactorPct] is a percentage, seeded from
 * [PerformanceCorrectionsData.MarginFactorDefaults.takeoffDefaultFactor] rather than a literal
 * here — never a hardcoded default deeper than this ViewModel's starting state, and always
 * user-adjustable. [slopePct] follows "positive = uphill" (AIC P173 §5.5), default 0%.
 *
 * Fase 2c ronde 3: there is no longer a single "chosen" runway or a confirmation gate
 * (rekenlogica.md §5) — once an airfield with a usable METAR and at least one runway is
 * selected, [runwayResults] holds every direction's own live result instead, and [result]
 * (the plain single-value calculation from [oatC]/[headwindKts]/[surfaceType]/[slopePct]) is
 * only shown when that per-runway list isn't applicable (Handmatig mode, or an airfield with
 * no METAR/no runways yet). [oatC]/[pressureAltM] still get silently overridden by the METAR
 * when [weatherMode] is [WeatherInputMode.METAR] — see `effective*` below — but headwind,
 * surface and slope no longer have a single derived value at all, since there's no one "the"
 * runway to derive them from anymore.
 */
data class TakeoffFormState(
    val registration: String? = null,
    val oatC: Int = 15,
    val pressureAltM: Int = 0,
    val headwindKts: Int = 0,
    val surfaceType: TakeoffSurfaceType = TakeoffSurfaceType.ASFALT,
    val slopePct: Int = 0,
    val marginFactorPct: Int = 133,
    val result: TakeoffResult? = null,
    val flightContextMode: FlightContextMode = FlightContextMode.MANUAL,
    val selectedAirfield: AirfieldEntity? = null,
    val runwayStrips: List<RunwayStripEntity> = emptyList(),
    val grassCondition: GrassCondition = GrassCondition.DRY,
    val weatherMode: WeatherInputMode = WeatherInputMode.METAR,
    val runwayResults: List<TakeoffRunwayResult> = emptyList(),
    /** True once an airfield with a usable (non-variable) METAR wind is selected — gates
     * whether OAT/pressure altitude can be derived at all this calculation. */
    val weatherDerivable: Boolean = false,
    /** [weatherDerivable] plus the METAR actually having a QNH group. */
    val pressureAltDerivable: Boolean = false,
    val effectiveOatC: Int = 15,
    val effectivePressureAltM: Int = 0
) {
    val hasGrassRunway: Boolean
        get() = runwayStrips.any { RunwaySurfaceType.valueOf(it.surface) == RunwaySurfaceType.GRASS }

    /** True when the live per-runway results list should replace the single manual result. */
    val showRunwayResults: Boolean
        get() = flightContextMode == FlightContextMode.AIRFIELD && selectedAirfield != null &&
            weatherDerivable && runwayResults.isNotEmpty()
}

class TakeoffViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    private val performanceNormal: PerformanceNormalData,
    private val corrections: PerformanceCorrectionsData
) : ViewModel() {

    val marginFactorDefaultPct: Int = (corrections.marginFactorDefaults.takeoffDefaultFactor * 100).roundToInt()

    private val _state = MutableStateFlow(TakeoffFormState(marginFactorPct = marginFactorDefaultPct))
    val state: StateFlow<TakeoffFormState> = _state

    /** Only favorited airfields, same "keep the picker fast" rule as favorite sailplane types. */
    val airfields: StateFlow<List<AirfieldEntity>> = combine(
        repository.observeAirfields(), repository.observeFavoriteAirfieldIds()
    ) { airfields, favoriteIds -> val idSet = favoriteIds.toSet(); airfields.filter { it.id in idSet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val loadGuard = LoadGuard()

    init {
        viewModelScope.launch {
            val entity = repository.getById(profileId)
            val savedInput = repository.getTakeoffInput(profileId)
            val flightContext = repository.getFlightContext(profileId)
            val selectedAirfield = flightContext?.airfieldId?.let { repository.getAirfield(it) }
            val runwayStrips = selectedAirfield?.let { repository.getRunwayStrips(it.id) } ?: emptyList()

            _state.update { current ->
                var next = current.copy(registration = entity?.registration)
                if (savedInput != null) {
                    next = next.copy(
                        oatC = savedInput.oatC,
                        pressureAltM = savedInput.pressureAltM,
                        headwindKts = savedInput.headwindKts,
                        surfaceType = TakeoffSurfaceType.valueOf(savedInput.surfaceType),
                        slopePct = savedInput.slopePct,
                        marginFactorPct = savedInput.marginFactorPct
                    )
                }
                if (flightContext != null) {
                    next = next.copy(
                        flightContextMode = FlightContextMode.valueOf(flightContext.mode),
                        grassCondition = GrassCondition.valueOf(flightContext.grassCondition),
                        selectedAirfield = selectedAirfield,
                        runwayStrips = runwayStrips
                    )
                }
                next
            }
            loadGuard.markLoaded()
            recalculate()
        }
    }

    fun update(transform: (TakeoffFormState) -> TakeoffFormState) {
        _state.update(transform)
        recalculate()
    }

    fun setFlightContextMode(mode: FlightContextMode) {
        _state.update { it.copy(flightContextMode = mode) }
        recalculate()
        saveFlightContext()
    }

    fun selectAirfield(airfield: AirfieldEntity) {
        viewModelScope.launch {
            val strips = repository.getRunwayStrips(airfield.id)
            _state.update { it.copy(selectedAirfield = airfield, runwayStrips = strips) }
            recalculate()
            saveFlightContext()
        }
    }

    fun setGrassCondition(condition: GrassCondition) {
        _state.update { it.copy(grassCondition = condition) }
        recalculate()
        saveFlightContext()
    }

    /** Switching to [WeatherInputMode.MANUAL] seeds OAT/pressure altitude from whatever the
     * METAR currently derives, so editing starts from those numbers rather than stale/default
     * ones — switching back to [WeatherInputMode.METAR] needs no such seeding, it just resumes
     * reading the METAR. */
    fun setWeatherMode(mode: WeatherInputMode) {
        _state.update {
            if (mode == WeatherInputMode.MANUAL) {
                it.copy(weatherMode = mode, oatC = it.effectiveOatC, pressureAltM = it.effectivePressureAltM)
            } else {
                it.copy(weatherMode = mode)
            }
        }
        recalculate()
    }

    private fun saveFlightContext() {
        val s = _state.value
        viewModelScope.launch {
            repository.saveFlightContext(
                FlightContextEntity(
                    profileId = profileId,
                    mode = s.flightContextMode.name,
                    airfieldId = s.selectedAirfield?.id,
                    grassCondition = s.grassCondition.name
                )
            )
        }
    }

    private fun surfaceFactorFor(type: TakeoffSurfaceType): Double = when (type) {
        TakeoffSurfaceType.ASFALT -> 1.0
        TakeoffSurfaceType.DROOG_GRAS -> 1.0 + performanceNormal.takeoff.grassRunwayPenaltyMinPct / 100.0
        TakeoffSurfaceType.NAT_GRAS -> corrections.grassTakeoffFactors.wetGrassFactor
        TakeoffSurfaceType.ZACHTE_GROND -> corrections.grassTakeoffFactors.softGroundFactor
    }

    private fun deriveSurfaceType(strip: RunwayStripEntity, grassCondition: GrassCondition): TakeoffSurfaceType =
        when (RunwaySurfaceType.valueOf(strip.surface)) {
            RunwaySurfaceType.ASPHALT -> TakeoffSurfaceType.ASFALT
            RunwaySurfaceType.GRASS -> when (grassCondition) {
                GrassCondition.DRY -> TakeoffSurfaceType.DROOG_GRAS
                GrassCondition.WET -> TakeoffSurfaceType.NAT_GRAS
                GrassCondition.SOFT -> TakeoffSurfaceType.ZACHTE_GROND
            }
        }

    /**
     * Fase 2c ronde 3 derivation: resolves OAT/pressure-altitude from the selected airfield's
     * METAR when [TakeoffFormState.weatherMode] is [WeatherInputMode.METAR], then — if the
     * METAR's wind has a usable direction — computes every runway direction's own live result
     * via [RunwayAdvisor] (headwind/surface/slope all come from that specific direction, never
     * a single "chosen" one). The plain manual [TakeoffResult] is always computed too, using
     * [TakeoffFormState.headwindKts]/[TakeoffFormState.surfaceType]/[TakeoffFormState.slopePct]
     * directly, for whenever [TakeoffFormState.showRunwayResults] is false. See rekenlogica.md
     * §5/§8; known limitation: if the wind is unusable, the OAT/pressure-altitude bundle falls
     * back to manual entry too (weatherMode is ignored) rather than deriving just those two
     * without a runway list — a simplification carried over from ronde 1.
     */
    private fun recalculate() {
        val s = _state.value

        val parsedMetar = s.selectedAirfield?.metarRaw
            ?.let { raw -> (MetarParser.parse(raw) as? MetarParseResult.Success)?.metar }

        val windDirection = parsedMetar?.windDirectionDeg
        val weatherDerivable = s.flightContextMode == FlightContextMode.AIRFIELD &&
            s.selectedAirfield != null && parsedMetar != null && windDirection != null
        val pressureAltDerivable = weatherDerivable && parsedMetar.qnhHpa != null

        val manualWeather = s.weatherMode == WeatherInputMode.MANUAL
        val effOat = if (!weatherDerivable || manualWeather) s.oatC else parsedMetar.temperatureC.roundToInt()
        val effPressureAlt = if (!pressureAltDerivable || manualWeather) {
            s.pressureAltM
        } else {
            PressureAltitude.fromElevationAndQnh(s.selectedAirfield.elevationM, parsedMetar.qnhHpa!!).roundToInt()
        }

        val directions: List<RunwayDirectionOption> = s.runwayStrips.flatMap { it.directionOptions() }
        val runwayResults: List<TakeoffRunwayResult> = if (weatherDerivable && directions.isNotEmpty()) {
            val fullResultsByDesignator = mutableMapOf<String, TakeoffResult>()
            val advice = RunwayAdvisor.advise(
                candidates = directions.map { it.toCandidate() },
                windDirectionDeg = windDirection,
                windSpeedKts = parsedMetar.windSpeedKts,
                demonstratedCrosswindKts = kmhToKts(performanceNormal.demonstratedCrosswindKmh),
                requiredDistances = { headwindKts, candidate ->
                    val direction = directions.first { it.id == candidate.designator }
                    val surfaceType = deriveSurfaceType(direction.strip, s.grassCondition)
                    val distance = PerformanceCalculator.calculateTakeoff(
                        performanceNormal, corrections,
                        oatC = effOat.toDouble(),
                        pressureAltM = effPressureAlt.toDouble(),
                        headwindKts = headwindKts,
                        surfaceFactor = surfaceFactorFor(surfaceType),
                        slopePct = candidate.slopePct,
                        marginFactor = s.marginFactorPct / 100.0
                    )
                    fullResultsByDesignator[candidate.designator] = distance
                    RequiredDistances(withMarginM = distance.s2WithMarginM, withoutMarginM = distance.s2M)
                }
            )
            advice.map { item ->
                val direction = directions.first { it.id == item.candidate.designator }
                TakeoffRunwayResult(
                    advice = item,
                    surfaceType = deriveSurfaceType(direction.strip, s.grassCondition),
                    fullResult = fullResultsByDesignator[item.candidate.designator]
                )
            }
        } else {
            emptyList()
        }

        val result = PerformanceCalculator.calculateTakeoff(
            performanceNormal, corrections,
            oatC = effOat.toDouble(),
            pressureAltM = effPressureAlt.toDouble(),
            headwindKts = s.headwindKts.toDouble(),
            surfaceFactor = surfaceFactorFor(s.surfaceType),
            slopePct = s.slopePct.toDouble(),
            marginFactor = s.marginFactorPct / 100.0
        )

        _state.update {
            it.copy(
                result = result,
                runwayResults = runwayResults,
                weatherDerivable = weatherDerivable,
                pressureAltDerivable = pressureAltDerivable,
                effectiveOatC = effOat,
                effectivePressureAltM = effPressureAlt
            )
        }

        loadGuard.runIfLoaded {
            viewModelScope.launch {
                repository.saveTakeoffInput(
                    TakeoffInputEntity(
                        profileId, s.oatC, s.pressureAltM, s.headwindKts,
                        s.surfaceType.name, s.slopePct, s.marginFactorPct, chosenRunwayDesignator = null
                    )
                )
            }
        }
    }

    companion object {
        fun factory(
            repository: AircraftProfileRepository,
            profileId: Long,
            performanceNormal: PerformanceNormalData,
            corrections: PerformanceCorrectionsData
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return TakeoffViewModel(repository, profileId, performanceNormal, corrections) as T
            }
        }
    }
}
