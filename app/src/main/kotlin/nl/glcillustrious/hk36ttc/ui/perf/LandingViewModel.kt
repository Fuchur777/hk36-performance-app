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
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdvice
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdviceStatus
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdvisor
import nl.glcillustrious.hk36ttc.core.metar.kmhToKts
import nl.glcillustrious.hk36ttc.core.perf.LandingResult
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCalculator
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextMode
import nl.glcillustrious.hk36ttc.data.local.GrassCondition
import nl.glcillustrious.hk36ttc.data.local.LandingInputEntity
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.RunwaySurfaceType
import nl.glcillustrious.hk36ttc.ui.common.LoadGuard
import nl.glcillustrious.hk36ttc.ui.common.RunwayDirectionOption
import nl.glcillustrious.hk36ttc.ui.common.WeatherInputMode
import nl.glcillustrious.hk36ttc.ui.common.directionOptions
import nl.glcillustrious.hk36ttc.ui.common.toCandidate

/** Supplement 11 publishes no landing correction for anything but a paved runway — the grass
 * factors come from AIC P173 instead, and "Aangepast" lets the pilot enter their own estimate
 * up to the AIC P173-published upper bound for very short, smooth grass. */
enum class LandingSurfaceType { ASFALT, DROOG_GRAS, NAT_GRAS, AANGEPAST }

/**
 * [slopePct] follows "positive = uphill", default 0% (AIC P173 §7.5 — only downhill is
 * adverse for landing). [marginFactorPct] is seeded from
 * [PerformanceCorrectionsData.MarginFactorDefaults.landingDefaultFactor], never a hardcoded
 * literal here.
 *
 * Fase 2c: same split as [TakeoffFormState] between raw/override fields and the `effective*`
 * fields actually used for [result] — see that class's KDoc. Landing has no headwind input at
 * all (the AFM landing tables aren't headwind-indexed), so unlike take-off there's no
 * headwind override here; wind is still shown/ranked for runway *choice* (tailwind landings
 * are excluded from the advisory as an airmanship default, not an AFM limitation — see
 * [LandingViewModel]'s recalculate KDoc).
 */
data class LandingFormState(
    val registration: String? = null,
    val oatC: Int = 15,
    val pressureAltM: Int = 0,
    val surfaceType: LandingSurfaceType = LandingSurfaceType.ASFALT,
    val customSurfaceFactorPct: Int = 115,
    val slopePct: Int = 0,
    val marginFactorPct: Int = 143,
    val result: LandingResult? = null,
    val flightContextMode: FlightContextMode = FlightContextMode.MANUAL,
    val selectedAirfield: AirfieldEntity? = null,
    val runwayStrips: List<RunwayStripEntity> = emptyList(),
    val grassCondition: GrassCondition = GrassCondition.DRY,
    val chosenRunwayDesignator: String? = null,
    val flightConfirmed: Boolean = false,
    val weatherMode: WeatherInputMode = WeatherInputMode.METAR,
    val surfaceOverridden: Boolean = false,
    val slopeOverridden: Boolean = false,
    val runwayAdvice: List<RunwayAdvice> = emptyList(),
    val weatherDerivable: Boolean = false,
    val pressureAltDerivable: Boolean = false,
    val effectiveOatC: Int = 15,
    val effectivePressureAltM: Int = 0,
    val effectiveSurfaceType: LandingSurfaceType = LandingSurfaceType.ASFALT,
    val effectiveCustomSurfaceFactorPct: Int = 115,
    val effectiveSlopePct: Int = 0
) {
    val hasGrassRunway: Boolean
        get() = runwayStrips.any { RunwaySurfaceType.valueOf(it.surface) == RunwaySurfaceType.GRASS }
}

class LandingViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    private val performanceNormal: PerformanceNormalData,
    private val corrections: PerformanceCorrectionsData
) : ViewModel() {

    val marginFactorDefaultPct: Int = (corrections.marginFactorDefaults.landingDefaultFactor * 100).roundToInt()

    private val _state = MutableStateFlow(LandingFormState(marginFactorPct = marginFactorDefaultPct))
    val state: StateFlow<LandingFormState> = _state

    /** Only favorited airfields, same "keep the picker fast" rule as favorite sailplane types. */
    val airfields: StateFlow<List<AirfieldEntity>> = combine(
        repository.observeAirfields(), repository.observeFavoriteAirfieldIds()
    ) { airfields, favoriteIds -> val idSet = favoriteIds.toSet(); airfields.filter { it.id in idSet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val loadGuard = LoadGuard()

    init {
        viewModelScope.launch {
            val entity = repository.getById(profileId)
            val savedInput = repository.getLandingInput(profileId)
            val flightContext = repository.getFlightContext(profileId)
            val selectedAirfield = flightContext?.airfieldId?.let { repository.getAirfield(it) }
            val runwayStrips = selectedAirfield?.let { repository.getRunwayStrips(it.id) } ?: emptyList()

            _state.update { current ->
                var next = current.copy(registration = entity?.registration)
                if (savedInput != null) {
                    next = next.copy(
                        oatC = savedInput.oatC,
                        pressureAltM = savedInput.pressureAltM,
                        surfaceType = LandingSurfaceType.valueOf(savedInput.surfaceType),
                        customSurfaceFactorPct = savedInput.customSurfaceFactorPct,
                        slopePct = savedInput.slopePct,
                        marginFactorPct = savedInput.marginFactorPct,
                        chosenRunwayDesignator = savedInput.chosenRunwayDesignator
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

    fun update(transform: (LandingFormState) -> LandingFormState) {
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
            _state.update {
                it.copy(selectedAirfield = airfield, runwayStrips = strips, chosenRunwayDesignator = null)
            }
            recalculate()
            saveFlightContext()
        }
    }

    fun setGrassCondition(condition: GrassCondition) {
        _state.update { it.copy(grassCondition = condition) }
        recalculate()
        saveFlightContext()
    }

    fun chooseRunway(designator: String?) {
        _state.update { it.copy(chosenRunwayDesignator = designator) }
        recalculate()
    }

    fun confirmFlightContext() {
        _state.update { it.copy(flightConfirmed = true) }
    }

    /** See [TakeoffViewModel.setWeatherMode] — same seed-then-switch behavior. */
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

    fun editSurfaceAndSlope() {
        _state.update {
            it.copy(
                surfaceOverridden = true,
                slopeOverridden = true,
                surfaceType = it.effectiveSurfaceType,
                customSurfaceFactorPct = it.effectiveCustomSurfaceFactorPct,
                slopePct = it.effectiveSlopePct
            )
        }
        recalculate()
    }

    fun resetSurfaceAndSlope() {
        _state.update { it.copy(surfaceOverridden = false, slopeOverridden = false) }
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

    private fun surfaceFactorFor(type: LandingSurfaceType, customSurfaceFactorPct: Int): Double = when (type) {
        LandingSurfaceType.ASFALT -> 1.0
        LandingSurfaceType.DROOG_GRAS -> corrections.grassLandingFactors.dryGrassFactor
        LandingSurfaceType.NAT_GRAS -> corrections.grassLandingFactors.wetGrassFactor
        LandingSurfaceType.AANGEPAST -> customSurfaceFactorPct / 100.0
    }

    /** Landing has no AFM grass-only "soft ground" category — [GrassCondition.SOFT] falls back
     * to Aangepast at the AIC P173 upper bound, the closest published figure, still fully
     * overridable. */
    private fun deriveSurfaceType(strip: RunwayStripEntity, grassCondition: GrassCondition): LandingSurfaceType =
        when (RunwaySurfaceType.valueOf(strip.surface)) {
            RunwaySurfaceType.ASPHALT -> LandingSurfaceType.ASFALT
            RunwaySurfaceType.GRASS -> when (grassCondition) {
                GrassCondition.DRY -> LandingSurfaceType.DROOG_GRAS
                GrassCondition.WET -> LandingSurfaceType.NAT_GRAS
                GrassCondition.SOFT -> LandingSurfaceType.AANGEPAST
            }
        }

    private fun deriveCustomSurfaceFactorPct(type: LandingSurfaceType): Int =
        if (type == LandingSurfaceType.AANGEPAST) maxCustomSurfaceFactorPct() else 115

    /**
     * Fase 2c derivation — see [TakeoffViewModel.recalculate] for the general pattern. Landing
     * has no headwind input, so wind only drives *which runway* is ranked/excluded, never a
     * calculation parameter: a tailwind heading is excluded from the advisory as a deliberate
     * airmanship default (landing downwind is always worse), not because the AFM tables lack
     * tailwind data the way take-off's do — `calculateLanding` doesn't take headwind at all.
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
        val advice: List<RunwayAdvice> = if (weatherDerivable && directions.isNotEmpty()) {
            RunwayAdvisor.advise(
                candidates = directions.map { it.toCandidate() },
                windDirectionDeg = windDirection,
                windSpeedKts = parsedMetar.windSpeedKts,
                demonstratedCrosswindKts = kmhToKts(performanceNormal.demonstratedCrosswindKmh),
                requiredDistanceM = { _, candidate ->
                    val direction = directions.first { it.designator == candidate.designator }
                    val surfaceType = deriveSurfaceType(direction.strip, s.grassCondition)
                    PerformanceCalculator.calculateLanding(
                        performanceNormal, corrections,
                        oatC = effOat.toDouble(),
                        pressureAltM = effPressureAlt.toDouble(),
                        surfaceFactor = surfaceFactorFor(surfaceType, deriveCustomSurfaceFactorPct(surfaceType)),
                        slopePct = candidate.slopePct,
                        marginFactor = s.marginFactorPct / 100.0
                    ).l2WithMarginM
                }
            )
        } else {
            emptyList()
        }

        val activeDirection = directions.find { it.designator == s.chosenRunwayDesignator }
            ?: directions.find { d -> advice.any { it.candidate.designator == d.designator && it.status == RunwayAdviceStatus.PREFERRED } }

        val effSurface = if (!weatherDerivable || s.surfaceOverridden || activeDirection == null) {
            s.surfaceType
        } else {
            deriveSurfaceType(activeDirection.strip, s.grassCondition)
        }
        val effCustomFactor = if (!weatherDerivable || s.surfaceOverridden || activeDirection == null) {
            s.customSurfaceFactorPct
        } else {
            deriveCustomSurfaceFactorPct(effSurface)
        }
        val effSlope = if (!weatherDerivable || s.slopeOverridden || activeDirection == null) {
            s.slopePct
        } else {
            activeDirection.slopePct.roundToInt()
        }

        val bundleChanged = s.effectiveOatC != effOat || s.effectivePressureAltM != effPressureAlt ||
            s.effectiveSurfaceType != effSurface || s.effectiveCustomSurfaceFactorPct != effCustomFactor ||
            s.effectiveSlopePct != effSlope
        val stillConfirmed = s.flightConfirmed && !bundleChanged

        val result = PerformanceCalculator.calculateLanding(
            performanceNormal, corrections,
            oatC = effOat.toDouble(),
            pressureAltM = effPressureAlt.toDouble(),
            surfaceFactor = surfaceFactorFor(effSurface, effCustomFactor),
            slopePct = effSlope.toDouble(),
            marginFactor = s.marginFactorPct / 100.0
        )

        _state.update {
            it.copy(
                result = result,
                runwayAdvice = advice,
                weatherDerivable = weatherDerivable,
                pressureAltDerivable = pressureAltDerivable,
                effectiveOatC = effOat,
                effectivePressureAltM = effPressureAlt,
                effectiveSurfaceType = effSurface,
                effectiveCustomSurfaceFactorPct = effCustomFactor,
                effectiveSlopePct = effSlope,
                flightConfirmed = stillConfirmed
            )
        }

        loadGuard.runIfLoaded {
            viewModelScope.launch {
                repository.saveLandingInput(
                    LandingInputEntity(
                        profileId, s.oatC, s.pressureAltM, s.surfaceType.name,
                        s.customSurfaceFactorPct, s.slopePct, s.marginFactorPct, s.chosenRunwayDesignator
                    )
                )
            }
        }
    }

    /** Upper bound for the "Aangepast" custom surface-factor stepper, from AIC P173. */
    fun maxCustomSurfaceFactorPct(): Int = (corrections.grassLandingFactors.veryShortGrassFactorUpperBound * 100).roundToInt()

    companion object {
        fun factory(
            repository: AircraftProfileRepository,
            profileId: Long,
            performanceNormal: PerformanceNormalData,
            corrections: PerformanceCorrectionsData
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return LandingViewModel(repository, profileId, performanceNormal, corrections) as T
            }
        }
    }
}
