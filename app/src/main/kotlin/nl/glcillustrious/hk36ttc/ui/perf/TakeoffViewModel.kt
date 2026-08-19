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
 * (rekenlogica.md §5) — once an airfield with at least one runway and a usable wind source
 * (METAR or, since ronde 4, manually typed) is selected, [runwayResults] holds every
 * direction's own live result instead, and [result] (the plain single-value calculation) is
 * only shown when that per-runway list isn't applicable (Handmatig mode with no airfield at
 * all, or an airfield with no runways yet). [oatC]/[pressureAltM] get silently overridden by
 * the METAR when [weatherMode] is [WeatherInputMode.METAR] — see `effective*` below.
 *
 * Fase 2c ronde 4: with an airfield selected and [weatherMode] set to
 * [WeatherInputMode.MANUAL], [windDirectionDeg]/[windSpeedKts] (a plain wind, not a
 * pre-resolved headwind) feed the exact same per-runway advisor the METAR would — this is
 * what lets Manual weather keep showing every runway's result with its own correct surface,
 * instead of falling back to asking for a single surface/slope/headwind by hand. Plain
 * Handmatig (no airfield at all) still uses the older [headwindKts]/[surfaceType]/[slopePct]
 * trio for its one manual result, unchanged since before Fase 2c.
 */
data class TakeoffFormState(
    val registration: String? = null,
    val oatC: Int = 15,
    val pressureAltM: Int = 0,
    val headwindKts: Int = 0,
    val windDirectionDeg: Int = 0,
    val windSpeedKts: Int = 0,
    /** True once the pilot has actually touched [windDirectionDeg]/[windSpeedKts] — 0°/0kt is
     * a real, valid calm wind, so an untouched default can't be told apart from a deliberately
     * entered calm wind by value alone. Without this, the per-runway results would silently
     * compute from an assumed calm wind before the pilot typed anything at all. */
    val windManuallySet: Boolean = false,
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
    /** True once an airfield with a successfully-parsed METAR is selected — temperature is
     * always present on a successful parse, so unlike wind this never depends on the wind
     * group being usable (rekenlogica.md §5: a variable/light wind doesn't make the METAR's
     * OAT any less real). Named `weatherDerivable` for historical reasons but only ever gated
     * OAT from ronde 4 onward — pressure-alt/wind have their own, independent flags below. */
    val weatherDerivable: Boolean = false,
    /** [weatherDerivable] plus the METAR actually having a QNH group — still independent of
     * wind usability. */
    val pressureAltDerivable: Boolean = false,
    /** True when the METAR's wind has a specific, usable heading (not `VRB`, not missing) —
     * the one thing that actually needs a fallback when it's not there, since a headwind
     * component can't be computed without it. */
    val metarWindUsable: Boolean = false,
    val effectiveOatC: Int = 15,
    val effectivePressureAltM: Int = 0,
    /** True while a pilot-requested METAR refresh (the "Controleer op nieuwe METAR" button on
     * [nl.glcillustrious.hk36ttc.ui.common.MetarSummary]) is in flight — the background
     * auto-refresh never touches this, since it's meant to stay silent (see
     * [TakeoffViewModel.autoRefreshMetarIfStale]). */
    val metarRefreshing: Boolean = false
) {
    val hasGrassRunway: Boolean
        get() = runwayStrips.anyGrass()

    /** The "gras vandaag" (droog/nat/zacht) choice only feeds the per-runway calculation, which
     * derives each runway's surface from its own strip. In [FlightContextMode.MANUAL] the pilot
     * picks the surface outright with the Ondergrond selector instead, so this selector would
     * sit there doing nothing — the selected airfield's runway list is deliberately kept in
     * state across a mode switch (so switching back doesn't lose it), which is why this needs
     * an explicit mode check rather than relying on [hasGrassRunway] alone. */
    val showGrassCondition: Boolean
        get() = flightContextMode == FlightContextMode.AIRFIELD && selectedAirfield != null && hasGrassRunway

    /** True when the live per-runway results list should replace the single manual result —
     * true whenever there's *any* usable wind source (METAR or manually typed) and at least
     * one runway, regardless of which [weatherMode] supplied that wind. */
    val showRunwayResults: Boolean
        get() = flightContextMode == FlightContextMode.AIRFIELD && selectedAirfield != null && runwayResults.isNotEmpty()

    /** True whenever the pilot needs to type [windDirectionDeg]/[windSpeedKts] by hand: either
     * they explicitly chose Handmatig weather, or they're still in METAR mode but the METAR's
     * own wind isn't usable (variable/missing) — rekenlogica.md §5. OAT/pressure-alt are NOT
     * bundled into this: they stay METAR-derived in the second case, since nothing about them
     * depends on the wind group at all. */
    val windNeedsManualEntry: Boolean
        get() = flightContextMode == FlightContextMode.AIRFIELD && selectedAirfield != null &&
            (weatherMode == WeatherInputMode.MANUAL || !metarWindUsable)

    /** True specifically for the "METAR mostly works, only the wind doesn't" case — this is
     * what the bold red "richting onbekend" hint in the UI is keyed on, since a pilot who
     * deliberately chose Handmatig doesn't need that explanation. */
    val windDirectionUnknown: Boolean
        get() = flightContextMode == FlightContextMode.AIRFIELD && selectedAirfield != null &&
            weatherMode == WeatherInputMode.METAR && !metarWindUsable
}

@OptIn(ExperimentalCoroutinesApi::class)
class TakeoffViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    private val performanceNormal: PerformanceNormalData,
    private val corrections: PerformanceCorrectionsData,
    private val metarRepository: MetarRepository? = null,
    private val metarConfig: MetarConfigData = MetarConfigData.DEFAULT
) : ViewModel() {

    val marginFactorDefaultPct: Int = (corrections.marginFactorDefaults.takeoffDefaultFactor * 100).roundToInt()

    private val _state = MutableStateFlow(TakeoffFormState(marginFactorPct = marginFactorDefaultPct))
    val state: StateFlow<TakeoffFormState> = _state

    /** Only favorited airfields, same "keep the picker fast" rule as favorite sailplane types. */
    val airfields: StateFlow<List<AirfieldEntity>> = combine(
        repository.observeAirfields(), repository.observeFavoriteAirfieldIds()
    ) { airfields, favoriteIds -> val idSet = favoriteIds.toSet(); airfields.filter { it.id in idSet } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The currently selected airfield's id, independent of [TakeoffFormState.selectedAirfield]
     * — the source of truth [selectedAirfield]/[runwayStrips] below are derived from, so that
     * editing the airfield's METAR or runways on a different screen is picked up here live
     * instead of leaving this screen holding a stale snapshot from whenever it was selected. */
    private val selectedAirfieldId = MutableStateFlow<Long?>(null)

    private val loadGuard = LoadGuard()

    init {
        viewModelScope.launch {
            val entity = repository.getById(profileId)
            val savedInput = repository.getTakeoffInput(profileId)
            val flightContext = repository.getFlightContext(profileId)

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
                        grassCondition = GrassCondition.valueOf(flightContext.grassCondition)
                    )
                }
                next
            }
            selectedAirfieldId.value = flightContext?.airfieldId
            loadGuard.markLoaded()
            // No recalculate() here: the combined airfield/runway collector below fires its
            // own first emission immediately (even for a null id) and owns recalculation from
            // here on. Calling it here too raced that collector — this coroutine could compute
            // a plain-manual result with flightContextMode already AIRFIELD but selectedAirfield
            // still null, transiently rendering the old manual fields before the real airfield
            // data arrived and corrected it.
        }

        // Keeps the selected airfield's own fields (METAR text in particular) and its runway
        // list live — editing either on the Airfield screen after selecting it here must be
        // reflected immediately, not only after re-picking it from the dropdown. The airfield
        // and strips sub-flows are combined *inside* one flatMapLatest keyed on the id, not as
        // two independently flatMapLatest'd flows fed into an outer combine(): combine() only
        // guarantees each source has emitted at least once, then re-emits pairing whichever
        // source just fired with the other's *last cached* value — so switching airfields could
        // pair the new airfield with the old airfield's still-cached runway strips (or vice
        // versa) for one recalculate() before the second source caught up. That mismatched
        // pairing is exactly what looked like the new wind-entry UI flashing correctly and then
        // reverting: one recalculate() briefly saw the new (VRB) airfield paired with the old
        // strips and rendered correctly, the next paired the old airfield's usable wind with the
        // new strips and rendered the old-style full result. Keying one flatMapLatest on the id
        // and combining inside it means the whole inner flow — both sources — gets torn down and
        // rebuilt together on every id change, so a value from one airfield can never be paired
        // with a value from another.
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

    /**
     * Quietly fetches a fresh METAR for [airfield] when the stored one is missing or old enough
     * (rekenlogica.md §5). Nothing here blocks or reports: the result flows back in through the
     * collector above, because the fetch writes to the same `airfields` row this screen is
     * already observing, so the weather simply updates itself.
     *
     * Failure is silent on purpose — a pilot on a field with no signal should see the screen keep
     * working with the previous or hand-entered weather, not an error they can do nothing about.
     * This only ever tries once per airfield per screen visit (see [MetarAutoRefresher]) — a
     * pilot who leaves the screen open longer than that has [refreshMetarNow] instead.
     */
    private fun autoRefreshMetarIfStale(airfield: AirfieldEntity?) {
        val repositoryForMetar = metarRepository ?: return
        if (airfield == null || _state.value.flightContextMode != FlightContextMode.AIRFIELD) return
        if (!metarRefresher.shouldAutoRefresh(airfield, metarConfig)) return

        viewModelScope.launch {
            runCatching { repositoryForMetar.refreshOne(airfield, metarConfig) }
        }
    }

    private val metarRefresher = MetarAutoRefresher()

    /**
     * The pilot explicitly asked for a fresh METAR (the button [MetarSummary] shows once its
     * report is stale) — unlike [autoRefreshMetarIfStale], this always attempts the fetch
     * regardless of [MetarAutoRefresher]'s once-per-visit bookkeeping, and reports back via
     * [TakeoffFormState.metarRefreshing] so the button can show it's working.
     */
    fun refreshMetarNow() {
        val repositoryForMetar = metarRepository ?: return
        val airfield = _state.value.selectedAirfield ?: return
        viewModelScope.launch {
            _state.update { it.copy(metarRefreshing = true) }
            runCatching { repositoryForMetar.refreshOne(airfield, metarConfig) }
            _state.update { it.copy(metarRefreshing = false) }
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
        selectedAirfieldId.value = airfield.id
        // A leftover manually-typed wind from a previously selected airfield must not silently
        // carry over and drive this new airfield's results before the pilot has looked at it.
        _state.update { it.copy(windManuallySet = false) }
        saveFlightContext()
    }

    fun setGrassCondition(condition: GrassCondition) {
        _state.update { it.copy(grassCondition = condition) }
        recalculate()
        saveFlightContext()
    }

    /** Switching to [WeatherInputMode.MANUAL] seeds OAT/pressure altitude (and, with an
     * airfield selected, wind direction/speed) from whatever the METAR currently derives, so
     * editing starts from those numbers rather than stale/default ones — switching back to
     * [WeatherInputMode.METAR] needs no such seeding, it just resumes reading the METAR. */
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
                    // Explicitly choosing Handmatig is itself the pilot's confirmation of
                    // whatever wind value that seeds — no need to wait for them to also nudge
                    // a stepper before showing results.
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

    private fun surfaceFactorFor(type: TakeoffSurfaceType): Double = when (type) {
        TakeoffSurfaceType.ASFALT -> 1.0
        TakeoffSurfaceType.DROOG_GRAS -> 1.0 + performanceNormal.takeoff.grassRunwayPenaltyMinPct / 100.0
        TakeoffSurfaceType.NAT_GRAS -> corrections.grassTakeoffFactors.wetGrassFactor
        TakeoffSurfaceType.ZACHTE_GROND -> corrections.grassTakeoffFactors.softGroundFactor
    }

    private fun deriveSurfaceType(strip: RunwayStripEntity, grassCondition: GrassCondition): TakeoffSurfaceType =
        when (strip.surfaceType()) {
            RunwaySurfaceType.ASPHALT -> TakeoffSurfaceType.ASFALT
            RunwaySurfaceType.GRASS -> when (grassCondition) {
                GrassCondition.DRY -> TakeoffSurfaceType.DROOG_GRAS
                GrassCondition.WET -> TakeoffSurfaceType.NAT_GRAS
                GrassCondition.SOFT -> TakeoffSurfaceType.ZACHTE_GROND
            }
        }

    /**
     * Fase 2c ronde 3/4 derivation: resolves OAT/pressure-altitude from the selected airfield's
     * METAR when [TakeoffFormState.weatherMode] is [WeatherInputMode.METAR], then picks a wind
     * source (METAR, or the pilot's own typed direction+speed in Manual mode) and — given at
     * least one runway — computes every direction's own live result via [RunwayAdvisor]
     * (headwind/surface/slope all come from that specific direction, never a single "chosen"
     * one). The plain manual [TakeoffResult] is always computed too, using
     * [TakeoffFormState.headwindKts]/[TakeoffFormState.surfaceType]/[TakeoffFormState.slopePct]
     * directly, for whenever [TakeoffFormState.showRunwayResults] is false (plain Handmatig, or
     * an airfield with no runways yet). See rekenlogica.md §5/§8.
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
        val runwayResults: List<TakeoffRunwayResult> = if (windDirectionDeg != null && directions.isNotEmpty()) {
            val fullResultsByDesignator = mutableMapOf<String, TakeoffResult>()
            val advice = RunwayAdvisor.advise(
                candidates = directions.map { it.toCandidate() },
                windDirectionDeg = windDirectionDeg,
                windSpeedKts = windSpeedKts,
                demonstratedCrosswindKts = kmhToKts(performanceNormal.demonstratedCrosswindKmh),
                windGustKts = weather.windGustKts,
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
                weatherDerivable = weather.weatherDerivable,
                pressureAltDerivable = weather.pressureAltDerivable,
                metarWindUsable = weather.metarWindUsable,
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
            corrections: PerformanceCorrectionsData,
            metarRepository: MetarRepository? = null,
            metarConfig: MetarConfigData = MetarConfigData.DEFAULT
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return TakeoffViewModel(
                    repository, profileId, performanceNormal, corrections, metarRepository, metarConfig
                ) as T
            }
        }
    }
}
