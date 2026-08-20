package nl.schellenberg.hk36ttc.ui.perf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.schellenberg.hk36ttc.core.metar.MetarConfigData
import nl.schellenberg.hk36ttc.core.metar.MetarParseResult
import nl.schellenberg.hk36ttc.core.metar.MetarParser
import nl.schellenberg.hk36ttc.core.metar.RequiredDistances
import nl.schellenberg.hk36ttc.core.metar.RunwayAdvice
import nl.schellenberg.hk36ttc.core.metar.RunwayAdvisor
import nl.schellenberg.hk36ttc.core.metar.kmhToKts
import nl.schellenberg.hk36ttc.core.perf.LandingResult
import nl.schellenberg.hk36ttc.core.perf.PerformanceCalculator
import nl.schellenberg.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.schellenberg.hk36ttc.core.perf.PerformanceNormalData
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.AirfieldEntity
import nl.schellenberg.hk36ttc.data.local.FlightContextEntity
import nl.schellenberg.hk36ttc.data.local.FlightContextMode
import nl.schellenberg.hk36ttc.data.local.GrassCondition
import nl.schellenberg.hk36ttc.data.local.LandingInputEntity
import nl.schellenberg.hk36ttc.data.local.RunwayStripEntity
import nl.schellenberg.hk36ttc.data.local.RunwaySurfaceType
import nl.schellenberg.hk36ttc.data.metar.MetarRepository
import nl.schellenberg.hk36ttc.ui.common.LoadGuard
import nl.schellenberg.hk36ttc.ui.common.MetarAutoRefresher
import nl.schellenberg.hk36ttc.ui.common.RunwayDirectionOption
import nl.schellenberg.hk36ttc.ui.common.WeatherInputMode
import nl.schellenberg.hk36ttc.ui.common.anyGrass
import nl.schellenberg.hk36ttc.ui.common.deriveWeather
import nl.schellenberg.hk36ttc.ui.common.directionOptions
import nl.schellenberg.hk36ttc.ui.common.surfaceType
import nl.schellenberg.hk36ttc.ui.common.toCandidate

/** Supplement 11 publishes no landing correction for anything but a paved runway — the grass
 * factors come from AIC P173 instead, and "Aangepast" lets the pilot enter their own estimate
 * up to the AIC P173-published upper bound for very short, smooth grass. */
enum class LandingSurfaceType { ASFALT, DROOG_GRAS, NAT_GRAS, AANGEPAST }

/** One runway direction's full landing result — see [TakeoffRunwayResult] for why [fullResult]
 * (carrying l1/l2) sits alongside [advice] (which only carries l2, the fit-deciding figure). */
data class LandingRunwayResult(
    val advice: RunwayAdvice,
    val surfaceType: LandingSurfaceType,
    val fullResult: LandingResult?
)

/**
 * [slopePct] follows "positive = uphill", default 0% (AIC P173 §7.5 — only downhill is
 * adverse for landing). [marginFactorPct] is seeded from
 * [PerformanceCorrectionsData.MarginFactorDefaults.landingDefaultFactor], never a hardcoded
 * literal here.
 *
 * Fase 2c ronde 3: see [TakeoffFormState] for the general shape — no single "chosen" runway or
 * confirmation gate anymore, [runwayResults] holds every direction's own live result instead.
 * Landing has no headwind input at all (the AFM landing tables aren't headwind-indexed); wind
 * still drives *which* runways are excluded as tailwind in the results list (an airmanship
 * default, not an AFM limitation — see [LandingViewModel]'s recalculate KDoc).
 *
 * Fase 2c ronde 4: every flag below mirrors [TakeoffFormState] one-for-one — see there for the
 * reasoning behind each. [windDirectionDeg]/[windSpeedKts]/[windManuallySet] let the pilot
 * supply the wind by hand when the METAR's own is variable/missing or they chose Handmatig,
 * which for landing decides tailwind exclusion and ranking rather than a distance.
 */
data class LandingFormState(
    val registration: String? = null,
    val oatC: Int = 15,
    val pressureAltM: Int = 0,
    val windDirectionDeg: Int = 0,
    val windSpeedKts: Int = 0,
    val windManuallySet: Boolean = false,
    val surfaceType: LandingSurfaceType = LandingSurfaceType.ASFALT,
    val customSurfaceFactorPct: Int = 115,
    val slopePct: Int = 0,
    val marginFactorPct: Int = 143,
    val result: LandingResult? = null,
    val flightContextMode: FlightContextMode = FlightContextMode.MANUAL,
    val selectedAirfield: AirfieldEntity? = null,
    val runwayStrips: List<RunwayStripEntity> = emptyList(),
    val grassCondition: GrassCondition = GrassCondition.DRY,
    val weatherMode: WeatherInputMode = WeatherInputMode.METAR,
    val runwayResults: List<LandingRunwayResult> = emptyList(),
    val weatherDerivable: Boolean = false,
    val pressureAltDerivable: Boolean = false,
    val metarWindUsable: Boolean = false,
    val effectiveOatC: Int = 15,
    val effectivePressureAltM: Int = 0,
    /** See [TakeoffFormState.metarRefreshing]. */
    val metarRefreshing: Boolean = false
) {
    val hasGrassRunway: Boolean
        get() = runwayStrips.anyGrass()

    /** See [TakeoffFormState.showGrassCondition]. */
    val showGrassCondition: Boolean
        get() = flightContextMode == FlightContextMode.AIRFIELD && selectedAirfield != null && hasGrassRunway

    /** See [TakeoffFormState.showRunwayResults]. */
    val showRunwayResults: Boolean
        get() = flightContextMode == FlightContextMode.AIRFIELD && selectedAirfield != null && runwayResults.isNotEmpty()

    /** See [TakeoffFormState.windNeedsManualEntry]. */
    val windNeedsManualEntry: Boolean
        get() = flightContextMode == FlightContextMode.AIRFIELD && selectedAirfield != null &&
            (weatherMode == WeatherInputMode.MANUAL || !metarWindUsable)

    /** See [TakeoffFormState.windDirectionUnknown]. */
    val windDirectionUnknown: Boolean
        get() = flightContextMode == FlightContextMode.AIRFIELD && selectedAirfield != null &&
            weatherMode == WeatherInputMode.METAR && !metarWindUsable
}

@OptIn(ExperimentalCoroutinesApi::class)
class LandingViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    private val performanceNormal: PerformanceNormalData,
    private val corrections: PerformanceCorrectionsData,
    private val metarRepository: MetarRepository? = null,
    private val metarConfig: MetarConfigData = MetarConfigData.DEFAULT
) : ViewModel() {

    val marginFactorDefaultPct: Int = (corrections.marginFactorDefaults.landingDefaultFactor * 100).roundToInt()

    private val _state = MutableStateFlow(LandingFormState(marginFactorPct = marginFactorDefaultPct))
    val state: StateFlow<LandingFormState> = _state

    /** Only favorited airfields, same "keep the picker fast" rule as favorite sailplane types. */
    val airfields: StateFlow<List<AirfieldEntity>> = combine(
        repository.observeAirfields(), repository.observeFavoriteAirfieldIds()
    ) { airfields, favoriteIds -> val idSet = favoriteIds.toSet(); airfields.filter { it.id in idSet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** See [TakeoffViewModel.selectedAirfieldId]. */
    private val selectedAirfieldId = MutableStateFlow<Long?>(null)

    private val loadGuard = LoadGuard()

    init {
        viewModelScope.launch {
            val entity = repository.getById(profileId)
            val savedInput = repository.getLandingInput(profileId)
            val flightContext = repository.getFlightContext(profileId)

            _state.update { current ->
                var next = current.copy(registration = entity?.registration)
                if (savedInput != null) {
                    next = next.copy(
                        oatC = savedInput.oatC,
                        pressureAltM = savedInput.pressureAltM,
                        surfaceType = LandingSurfaceType.valueOf(savedInput.surfaceType),
                        customSurfaceFactorPct = savedInput.customSurfaceFactorPct,
                        slopePct = savedInput.slopePct,
                        marginFactorPct = savedInput.marginFactorPct
                    )
                }
                if (flightContext != null) {
                    next = next.copy(
                        flightContextMode = FlightContextMode.valueOf(flightContext.mode),
                        grassCondition = GrassCondition.valueOf(flightContext.grassCondition)
                    )
                }
                next
            }
            selectedAirfieldId.value = flightContext?.airfieldId
            loadGuard.markLoaded()
            // No recalculate() here — the collector below owns it. See TakeoffViewModel.init.
        }

        // See TakeoffViewModel.init for why airfield and strips are combined *inside* one
        // flatMapLatest rather than as two independently keyed flows.
        viewModelScope.launch {
            selectedAirfieldId.flatMapLatest { id ->
                if (id == null) {
                    flowOf<Pair<AirfieldEntity?, List<RunwayStripEntity>>>(null to emptyList())
                } else {
                    combine(
                        repository.observeAirfields().map { list -> list.find { it.id == id } },
                        repository.observeRunwayStrips(id)
                    ) { airfield, strips -> airfield to strips }
                }
            }.collect { (airfield, strips) ->
                _state.update { it.copy(selectedAirfield = airfield, runwayStrips = strips) }
                recalculate()
                autoRefreshMetarIfStale(airfield)
            }
        }
    }

    /** See [TakeoffViewModel.autoRefreshMetarIfStale]. */
    private fun autoRefreshMetarIfStale(airfield: AirfieldEntity?) {
        val repositoryForMetar = metarRepository ?: return
        if (airfield == null || _state.value.flightContextMode != FlightContextMode.AIRFIELD) return
        if (!metarRefresher.shouldAutoRefresh(airfield, metarConfig)) return

        viewModelScope.launch {
            runCatching { repositoryForMetar.refreshOne(airfield, metarConfig) }
        }
    }

    private val metarRefresher = MetarAutoRefresher()

    /** See [TakeoffViewModel.refreshMetarNow]. */
    fun refreshMetarNow() {
        val repositoryForMetar = metarRepository ?: return
        val airfield = _state.value.selectedAirfield ?: return
        viewModelScope.launch {
            _state.update { it.copy(metarRefreshing = true) }
            runCatching { repositoryForMetar.refreshOne(airfield, metarConfig) }
            _state.update { it.copy(metarRefreshing = false) }
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
        selectedAirfieldId.value = airfield.id
        _state.update { it.copy(windManuallySet = false) }
        saveFlightContext()
    }

    fun setGrassCondition(condition: GrassCondition) {
        _state.update { it.copy(grassCondition = condition) }
        recalculate()
        saveFlightContext()
    }

    /** See [TakeoffViewModel.setWeatherMode] — same seed-then-switch behavior. */
    fun setWeatherMode(mode: WeatherInputMode) {
        _state.update {
            if (mode == WeatherInputMode.MANUAL) {
                val parsedMetar = it.selectedAirfield?.metarRaw
                    ?.let { raw -> (MetarParser.parse(raw) as? MetarParseResult.Success)?.metar }
                it.copy(
                    weatherMode = mode,
                    oatC = it.effectiveOatC,
                    pressureAltM = it.effectivePressureAltM,
                    windDirectionDeg = parsedMetar?.windDirectionDeg?.roundToInt() ?: it.windDirectionDeg,
                    windSpeedKts = parsedMetar?.windSpeedKts?.roundToInt() ?: it.windSpeedKts,
                    windManuallySet = true
                )
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
                    airfieldId = selectedAirfieldId.value,
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
        when (strip.surfaceType()) {
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
     * Fase 2c ronde 3 derivation — see [TakeoffViewModel.recalculate] for the general pattern.
     * Landing has no headwind input, so wind only drives *which runway* is ranked/excluded,
     * never a calculation parameter: a tailwind heading is excluded from the results as a
     * deliberate airmanship default (landing downwind is always worse), not because the AFM
     * tables lack tailwind data the way take-off's do — `calculateLanding` doesn't take
     * headwind at all.
     */
    private fun recalculate() {
        val s = _state.value

        val weather = deriveWeather(
            selectedAirfield = s.selectedAirfield,
            airfieldContextActive = s.flightContextMode == FlightContextMode.AIRFIELD,
            manualWeather = s.weatherMode == WeatherInputMode.MANUAL,
            manualOatC = s.oatC,
            manualPressureAltM = s.pressureAltM,
            manualWindDirectionDeg = s.windDirectionDeg,
            manualWindSpeedKts = s.windSpeedKts,
            windManuallySet = s.windManuallySet
        )
        val effOat = weather.oatC
        val effPressureAlt = weather.pressureAltM
        val windDirectionDeg = weather.windDirectionDeg
        val windSpeedKts = weather.windSpeedKts

        val directions: List<RunwayDirectionOption> = s.runwayStrips.flatMap { it.directionOptions() }
        val runwayResults: List<LandingRunwayResult> = if (windDirectionDeg != null && directions.isNotEmpty()) {
            val fullResultsByDesignator = mutableMapOf<String, LandingResult>()
            val advice = RunwayAdvisor.advise(
                candidates = directions.map { it.toCandidate() },
                windDirectionDeg = windDirectionDeg,
                windSpeedKts = windSpeedKts,
                demonstratedCrosswindKts = kmhToKts(performanceNormal.demonstratedCrosswindKmh),
                windGustKts = weather.windGustKts,
                requiredDistances = { _, candidate ->
                    val direction = directions.first { it.id == candidate.designator }
                    val surfaceType = deriveSurfaceType(direction.strip, s.grassCondition)
                    val distance = PerformanceCalculator.calculateLanding(
                        performanceNormal, corrections,
                        oatC = effOat.toDouble(),
                        pressureAltM = effPressureAlt.toDouble(),
                        surfaceFactor = surfaceFactorFor(surfaceType, deriveCustomSurfaceFactorPct(surfaceType)),
                        slopePct = candidate.slopePct,
                        marginFactor = s.marginFactorPct / 100.0
                    )
                    fullResultsByDesignator[candidate.designator] = distance
                    RequiredDistances(withMarginM = distance.l2WithMarginM, withoutMarginM = distance.l2M)
                }
            )
            advice.map { item ->
                val direction = directions.first { it.id == item.candidate.designator }
                LandingRunwayResult(
                    advice = item,
                    surfaceType = deriveSurfaceType(direction.strip, s.grassCondition),
                    fullResult = fullResultsByDesignator[item.candidate.designator]
                )
            }
        } else {
            emptyList()
        }

        val result = PerformanceCalculator.calculateLanding(
            performanceNormal, corrections,
            oatC = effOat.toDouble(),
            pressureAltM = effPressureAlt.toDouble(),
            surfaceFactor = surfaceFactorFor(s.surfaceType, s.customSurfaceFactorPct),
            slopePct = s.slopePct.toDouble(),
            marginFactor = s.marginFactorPct / 100.0
        )

        _state.update {
            it.copy(
                result = result,
                runwayResults = runwayResults,
                weatherDerivable = weather.weatherDerivable,
                pressureAltDerivable = weather.pressureAltDerivable,
                metarWindUsable = weather.metarWindUsable,
                effectiveOatC = effOat,
                effectivePressureAltM = effPressureAlt
            )
        }

        loadGuard.runIfLoaded {
            viewModelScope.launch {
                repository.saveLandingInput(
                    LandingInputEntity(
                        profileId, s.oatC, s.pressureAltM, s.surfaceType.name,
                        s.customSurfaceFactorPct, s.slopePct, s.marginFactorPct, chosenRunwayDesignator = null
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
            corrections: PerformanceCorrectionsData,
            metarRepository: MetarRepository? = null,
            metarConfig: MetarConfigData = MetarConfigData.DEFAULT
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return LandingViewModel(
                    repository, profileId, performanceNormal, corrections, metarRepository, metarConfig
                ) as T
            }
        }
    }
}
