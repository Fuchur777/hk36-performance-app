package nl.glcillustrious.hk36ttc.ui.perf

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
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.core.metar.MetarParseResult
import nl.glcillustrious.hk36ttc.core.metar.MetarParser
import nl.glcillustrious.hk36ttc.core.metar.RequiredDistances
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdvice
import nl.glcillustrious.hk36ttc.core.metar.RunwayAdvisor
import nl.glcillustrious.hk36ttc.core.metar.kmhToKts
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceTowData
import nl.glcillustrious.hk36ttc.core.perf.SailplaneTypesData
import nl.glcillustrious.hk36ttc.core.perf.TowClassSelectionResult
import nl.glcillustrious.hk36ttc.core.perf.TowPerformanceCalculator
import nl.glcillustrious.hk36ttc.core.perf.TowTakeoffResult
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextMode
import nl.glcillustrious.hk36ttc.data.local.GrassCondition
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.RunwaySurfaceType
import nl.glcillustrious.hk36ttc.data.local.SleepvluchtInputEntity
import nl.glcillustrious.hk36ttc.data.metar.MetarRepository
import nl.glcillustrious.hk36ttc.ui.common.LoadGuard
import nl.glcillustrious.hk36ttc.ui.common.MetarAutoRefresher
import nl.glcillustrious.hk36ttc.ui.common.RunwayDirectionOption
import nl.glcillustrious.hk36ttc.ui.common.WeatherInputMode
import nl.glcillustrious.hk36ttc.ui.common.anyGrass
import nl.glcillustrious.hk36ttc.ui.common.deriveWeather
import nl.glcillustrious.hk36ttc.ui.common.directionOptions
import nl.glcillustrious.hk36ttc.ui.common.surfaceType
import nl.glcillustrious.hk36ttc.ui.common.toCandidate

/**
 * The AFM Sup 1 tow table is itself baselined on dry grass ("level short dry grass runway",
 * §5.2.3.1), so "Droog gras" uses the table unchanged (factor 1.0) — no conversion, no user
 * estimate. Every other surface is derived from that same table using the ratio already
 * implied by the normal take-off table, the only place both an asphalt AND a dry-grass figure
 * are published for this aircraft (`performance_normal.json`, AFM §5.3.3, dry-grass penalty
 * factor — currently 1.20): dividing the tow table by that factor gives the asphalt-equivalent
 * distance, and multiplying that by the AIC P173 wet-grass/soft-ground factors gives the other
 * two. See rekenlogica.md §2.3 step 7 and [SleepvluchtViewModel.surfaceFactorFor] — nothing
 * here is a user-adjustable estimate anymore.
 */
enum class SleepvluchtSurfaceType { ASFALT, DROOG_GRAS, NAT_GRAS, ZACHT }

/** One runway direction's full tow take-off result — see [TakeoffRunwayResult] for why
 * [fullResult] (carrying s1/s2) sits alongside [advice] (which only carries s2). */
data class SleepvluchtRunwayResult(
    val advice: RunwayAdvice,
    val surfaceType: SleepvluchtSurfaceType,
    val fullResult: TowTakeoffResult?
)

/** [slopePct] follows "positive = uphill" (AIC P173 §5.5, same convention and adverse
 * direction as normal take-off), default 0%.
 *
 * Fase 2c ronde 3: see [TakeoffFormState] for the general shape — no single "chosen" runway or
 * confirmation gate anymore, [runwayResults] holds every direction's own live result instead.
 * Unlike Take-off/Landing, this list is still only meaningful once sailplane/towplane mass are
 * known, so it's rendered at the bottom of [SleepvluchtScreen] rather than right after the
 * weather section — see that screen for the ordering.
 */
data class SleepvluchtFormState(
    val registration: String? = null,
    val oatC: Int = 15,
    val pressureAltM: Int = 0,
    val headwindKts: Int = 0,
    val windDirectionDeg: Int = 0,
    val windSpeedKts: Int = 0,
    val windManuallySet: Boolean = false,
    val slopePct: Int = 0,
    val marginFactorPct: Int = 133,
    val sailplaneMassKg: Int = 300,
    val ldRatioKnown: Boolean = false,
    val ldRatio: Int = 30,
    val instructionFlight: Boolean = false,
    val surfaceType: SleepvluchtSurfaceType = SleepvluchtSurfaceType.DROOG_GRAS,
    /** Name of the favorited zweeftype currently auto-filling [sailplaneMassKg]/[ldRatio], or
     * null when those fields are manually entered. See rekenlogica.md §2.3b. */
    val selectedSailplaneTypeName: String? = null,
    /** True when MTOW didn't fit any AFM class for the selected type, so [sailplaneMassKg] was
     * filled with leeggewicht+75kg instead — shown to the user, never silent. */
    val selectedSailplaneTypeUsedFallback: Boolean = false,
    /** Last W&B total mass for this registration, if one has been computed yet. */
    val towplaneMassFromWbKg: Int? = null,
    val towplaneMassManualOverride: Boolean = false,
    val towplaneMassManualKg: Int = 720,
    val result: TowTakeoffResult? = null,
    val flightContextMode: FlightContextMode = FlightContextMode.MANUAL,
    val selectedAirfield: AirfieldEntity? = null,
    val runwayStrips: List<RunwayStripEntity> = emptyList(),
    val grassCondition: GrassCondition = GrassCondition.DRY,
    val weatherMode: WeatherInputMode = WeatherInputMode.METAR,
    val runwayResults: List<SleepvluchtRunwayResult> = emptyList(),
    val weatherDerivable: Boolean = false,
    val pressureAltDerivable: Boolean = false,
    val metarWindUsable: Boolean = false,
    val effectiveOatC: Int = 15,
    val effectivePressureAltM: Int = 0,
    /** See [TakeoffFormState.metarRefreshing]. */
    val metarRefreshing: Boolean = false
) {
    /** The weight actually used for the calculation: the W&B value unless there isn't one
     * yet, or the user has explicitly chosen to override it. */
    val effectiveTowplaneMassKg: Int
        get() = if (towplaneMassManualOverride || towplaneMassFromWbKg == null) {
            towplaneMassManualKg
        } else {
            towplaneMassFromWbKg
        }

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
class SleepvluchtViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    private val performanceTow: PerformanceTowData,
    private val performanceNormal: PerformanceNormalData,
    private val corrections: PerformanceCorrectionsData,
    private val sailplaneTypes: SailplaneTypesData,
    private val metarRepository: MetarRepository? = null,
    private val metarConfig: MetarConfigData = MetarConfigData.DEFAULT
) : ViewModel() {

    val marginFactorDefaultPct: Int = (corrections.marginFactorDefaults.takeoffDefaultFactor * 100).roundToInt()

    private val _state = MutableStateFlow(SleepvluchtFormState(marginFactorPct = marginFactorDefaultPct))
    val state: StateFlow<SleepvluchtFormState> = _state

    val favoriteSailplaneTypes: StateFlow<List<SailplaneTypesData.SailplaneType>> =
        repository.observeFavoriteSailplaneTypeNames()
            .map { names -> val nameSet = names.toSet(); sailplaneTypes.types.filter { it.name in nameSet } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            val lastWb = repository.getLastWbResult(profileId)
            val savedInput = repository.getSleepvluchtInput(profileId)
            val flightContext = repository.getFlightContext(profileId)

            _state.update { current ->
                var next = current.copy(
                    registration = entity?.registration,
                    towplaneMassFromWbKg = lastWb?.totalMassKg?.roundToInt()
                )
                if (savedInput != null) {
                    next = next.copy(
                        oatC = savedInput.oatC,
                        pressureAltM = savedInput.pressureAltM,
                        headwindKts = savedInput.headwindKts,
                        slopePct = savedInput.slopePct,
                        marginFactorPct = savedInput.marginFactorPct,
                        sailplaneMassKg = savedInput.sailplaneMassKg,
                        ldRatioKnown = savedInput.ldRatioKnown,
                        ldRatio = savedInput.ldRatio,
                        instructionFlight = savedInput.instructionFlight,
                        surfaceType = SleepvluchtSurfaceType.valueOf(savedInput.surfaceType),
                        selectedSailplaneTypeName = savedInput.selectedSailplaneTypeName,
                        selectedSailplaneTypeUsedFallback = savedInput.selectedSailplaneTypeUsedFallback,
                        towplaneMassManualOverride = savedInput.towplaneMassManualOverride,
                        towplaneMassManualKg = savedInput.towplaneMassManualKg
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

    fun update(transform: (SleepvluchtFormState) -> SleepvluchtFormState) {
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

    private fun deriveSurfaceType(strip: RunwayStripEntity, grassCondition: GrassCondition): SleepvluchtSurfaceType =
        when (strip.surfaceType()) {
            RunwaySurfaceType.ASPHALT -> SleepvluchtSurfaceType.ASFALT
            RunwaySurfaceType.GRASS -> when (grassCondition) {
                GrassCondition.DRY -> SleepvluchtSurfaceType.DROOG_GRAS
                GrassCondition.WET -> SleepvluchtSurfaceType.NAT_GRAS
                GrassCondition.SOFT -> SleepvluchtSurfaceType.ZACHT
            }
        }

    /**
     * Auto-fills sailplaneMassKg/L-D from [type], per rekenlogica.md §2.3b: try MTOW through
     * the same class-selection-rule the calculator itself uses; if no class fits, fall back to
     * leeggewicht+75kg (the standard 75kg pilot weight, see §1). Which candidate was used is
     * only a display hint here — the actual fit/block outcome always comes from the normal
     * [TowPerformanceCalculator.calculateTowTakeoff] call in [recalculate], respecting whatever
     * instructievlucht/marge/helling the user currently has set.
     */
    fun selectSailplaneType(type: SailplaneTypesData.SailplaneType) {
        val mtowFits = TowPerformanceCalculator.selectTowClass(performanceTow, type.mtowKg, type.ldRatio) is
            TowClassSelectionResult.Selected
        val weightKg = if (mtowFits) type.mtowKg else type.emptyMassKg + 75.0
        update {
            it.copy(
                selectedSailplaneTypeName = type.name,
                selectedSailplaneTypeUsedFallback = !mtowFits,
                sailplaneMassKg = weightKg.roundToInt(),
                ldRatioKnown = true,
                ldRatio = type.ldRatio.roundToInt()
            )
        }
    }

    fun clearSailplaneTypeSelection() {
        update { it.copy(selectedSailplaneTypeName = null, selectedSailplaneTypeUsedFallback = false) }
    }

    /** The dry-grass penalty factor from the normal take-off table (AFM §5.3.3, e.g. 1.20) —
     * the only place both an asphalt and a dry-grass figure are published for this aircraft,
     * so it's the pivot every Sleepvlucht surface option is derived from. */
    private val dryGrassFactorFromNormalTakeoff: Double
        get() = 1.0 + performanceNormal.takeoff.grassRunwayPenaltyMinPct / 100.0

    /** The combined surface-correction factor for [type], applied directly to the (already
     * dry-grass-baselined) tow table — see rekenlogica.md §2.3 step 7. Not user-adjustable. */
    fun surfaceFactorFor(type: SleepvluchtSurfaceType): Double = when (type) {
        SleepvluchtSurfaceType.DROOG_GRAS -> 1.0
        SleepvluchtSurfaceType.ASFALT -> 1.0 / dryGrassFactorFromNormalTakeoff
        SleepvluchtSurfaceType.NAT_GRAS -> corrections.grassTakeoffFactors.wetGrassFactor / dryGrassFactorFromNormalTakeoff
        SleepvluchtSurfaceType.ZACHT -> corrections.grassTakeoffFactors.softGroundFactor / dryGrassFactorFromNormalTakeoff
    }

    /** Fase 2c ronde 3 derivation — see [TakeoffViewModel.recalculate] for the general pattern;
     * the tow calculator's own headwind/surface/slope parameters mirror take-off's exactly, so
     * the derivation logic is identical, just routed through [TowPerformanceCalculator]. */
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
        val runwayResults: List<SleepvluchtRunwayResult> = if (windDirectionDeg != null && directions.isNotEmpty()) {
            val fullResultsByDesignator = mutableMapOf<String, TowTakeoffResult>()
            val advice = RunwayAdvisor.advise(
                candidates = directions.map { it.toCandidate() },
                windDirectionDeg = windDirectionDeg,
                windSpeedKts = windSpeedKts,
                demonstratedCrosswindKts = kmhToKts(performanceTow.limits.demonstratedCrosswindKmh),
                windGustKts = weather.windGustKts,
                requiredDistances = { headwindKts, candidate ->
                    val direction = directions.first { it.id == candidate.designator }
                    val surfaceType = deriveSurfaceType(direction.strip, s.grassCondition)
                    val distance = TowPerformanceCalculator.calculateTowTakeoff(
                        performanceTow, corrections,
                        sailplaneMassKg = s.sailplaneMassKg.toDouble(),
                        ldRatio = if (s.ldRatioKnown) s.ldRatio.toDouble() else null,
                        instructionFlight = s.instructionFlight,
                        towplaneMassKg = s.effectiveTowplaneMassKg.toDouble(),
                        oatC = effOat.toDouble(),
                        pressureAltM = effPressureAlt.toDouble(),
                        headwindKts = headwindKts,
                        slopePct = candidate.slopePct,
                        marginFactor = s.marginFactorPct / 100.0,
                        surfaceCorrectionFactor = surfaceFactorFor(surfaceType)
                    )
                    fullResultsByDesignator[candidate.designator] = distance
                    RequiredDistances(withMarginM = distance.s2WithMarginM, withoutMarginM = distance.s2M)
                }
            )
            advice.map { item ->
                val direction = directions.first { it.id == item.candidate.designator }
                SleepvluchtRunwayResult(
                    advice = item,
                    surfaceType = deriveSurfaceType(direction.strip, s.grassCondition),
                    fullResult = fullResultsByDesignator[item.candidate.designator]
                )
            }
        } else {
            emptyList()
        }

        val result = TowPerformanceCalculator.calculateTowTakeoff(
            performanceTow,
            corrections,
            sailplaneMassKg = s.sailplaneMassKg.toDouble(),
            ldRatio = if (s.ldRatioKnown) s.ldRatio.toDouble() else null,
            instructionFlight = s.instructionFlight,
            towplaneMassKg = s.effectiveTowplaneMassKg.toDouble(),
            oatC = effOat.toDouble(),
            pressureAltM = effPressureAlt.toDouble(),
            headwindKts = s.headwindKts.toDouble(),
            slopePct = s.slopePct.toDouble(),
            marginFactor = s.marginFactorPct / 100.0,
            surfaceCorrectionFactor = surfaceFactorFor(s.surfaceType)
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
                repository.saveSleepvluchtInput(
                    SleepvluchtInputEntity(
                        profileId = profileId,
                        oatC = s.oatC,
                        pressureAltM = s.pressureAltM,
                        headwindKts = s.headwindKts,
                        slopePct = s.slopePct,
                        marginFactorPct = s.marginFactorPct,
                        sailplaneMassKg = s.sailplaneMassKg,
                        ldRatioKnown = s.ldRatioKnown,
                        ldRatio = s.ldRatio,
                        instructionFlight = s.instructionFlight,
                        surfaceType = s.surfaceType.name,
                        selectedSailplaneTypeName = s.selectedSailplaneTypeName,
                        selectedSailplaneTypeUsedFallback = s.selectedSailplaneTypeUsedFallback,
                        towplaneMassManualOverride = s.towplaneMassManualOverride,
                        towplaneMassManualKg = s.towplaneMassManualKg,
                        chosenRunwayDesignator = null
                    )
                )
            }
        }
    }

    companion object {
        fun factory(
            repository: AircraftProfileRepository,
            profileId: Long,
            performanceTow: PerformanceTowData,
            performanceNormal: PerformanceNormalData,
            corrections: PerformanceCorrectionsData,
            sailplaneTypes: SailplaneTypesData,
            metarRepository: MetarRepository? = null,
            metarConfig: MetarConfigData = MetarConfigData.DEFAULT
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return SleepvluchtViewModel(
                    repository, profileId, performanceTow, performanceNormal, corrections, sailplaneTypes,
                    metarRepository, metarConfig
                ) as T
            }
        }
    }
}
