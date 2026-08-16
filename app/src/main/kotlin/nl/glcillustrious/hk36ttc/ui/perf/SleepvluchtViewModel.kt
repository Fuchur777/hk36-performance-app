package nl.glcillustrious.hk36ttc.ui.perf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
import nl.glcillustrious.hk36ttc.ui.common.LoadGuard
import nl.glcillustrious.hk36ttc.ui.common.RunwayDirectionOption
import nl.glcillustrious.hk36ttc.ui.common.directionOptions
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

/** [slopePct] follows "positive = uphill" (AIC P173 §5.5, same convention and adverse
 * direction as normal take-off), default 0%. */
data class SleepvluchtFormState(
    val registration: String? = null,
    val oatC: Int = 15,
    val pressureAltM: Int = 0,
    val headwindKts: Int = 0,
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
    val chosenRunwayDesignator: String? = null,
    val flightConfirmed: Boolean = false,
    val oatOverridden: Boolean = false,
    val pressureAltOverridden: Boolean = false,
    val headwindOverridden: Boolean = false,
    val surfaceOverridden: Boolean = false,
    val slopeOverridden: Boolean = false,
    val runwayAdvice: List<RunwayAdvice> = emptyList(),
    val weatherDerivable: Boolean = false,
    val pressureAltDerivable: Boolean = false,
    val effectiveOatC: Int = 15,
    val effectivePressureAltM: Int = 0,
    val effectiveHeadwindKts: Int = 0,
    val effectiveSurfaceType: SleepvluchtSurfaceType = SleepvluchtSurfaceType.DROOG_GRAS,
    val effectiveSlopePct: Int = 0
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
        get() = runwayStrips.any { RunwaySurfaceType.valueOf(it.surface) == RunwaySurfaceType.GRASS }
}

class SleepvluchtViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    private val performanceTow: PerformanceTowData,
    private val performanceNormal: PerformanceNormalData,
    private val corrections: PerformanceCorrectionsData,
    private val sailplaneTypes: SailplaneTypesData
) : ViewModel() {

    val marginFactorDefaultPct: Int = (corrections.marginFactorDefaults.takeoffDefaultFactor * 100).roundToInt()

    private val _state = MutableStateFlow(SleepvluchtFormState(marginFactorPct = marginFactorDefaultPct))
    val state: StateFlow<SleepvluchtFormState> = _state

    val favoriteSailplaneTypes: StateFlow<List<SailplaneTypesData.SailplaneType>> =
        repository.observeFavoriteSailplaneTypeNames()
            .map { names -> val nameSet = names.toSet(); sailplaneTypes.types.filter { it.name in nameSet } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val airfields: StateFlow<List<AirfieldEntity>> = repository.observeAirfields()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val loadGuard = LoadGuard()

    init {
        viewModelScope.launch {
            val entity = repository.getById(profileId)
            val lastWb = repository.getLastWbResult(profileId)
            val savedInput = repository.getSleepvluchtInput(profileId)
            val flightContext = repository.getFlightContext(profileId)
            val selectedAirfield = flightContext?.airfieldId?.let { repository.getAirfield(it) }
            val runwayStrips = selectedAirfield?.let { repository.getRunwayStrips(it.id) } ?: emptyList()

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
                        towplaneMassManualKg = savedInput.towplaneMassManualKg,
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

    fun editOat() {
        _state.update { it.copy(oatOverridden = true, oatC = it.effectiveOatC) }
        recalculate()
    }

    fun resetOat() {
        _state.update { it.copy(oatOverridden = false) }
        recalculate()
    }

    fun editPressureAlt() {
        _state.update { it.copy(pressureAltOverridden = true, pressureAltM = it.effectivePressureAltM) }
        recalculate()
    }

    fun resetPressureAlt() {
        _state.update { it.copy(pressureAltOverridden = false) }
        recalculate()
    }

    fun editHeadwind() {
        _state.update { it.copy(headwindOverridden = true, headwindKts = it.effectiveHeadwindKts) }
        recalculate()
    }

    fun resetHeadwind() {
        _state.update { it.copy(headwindOverridden = false) }
        recalculate()
    }

    fun editSurfaceAndSlope() {
        _state.update {
            it.copy(
                surfaceOverridden = true,
                slopeOverridden = true,
                surfaceType = it.effectiveSurfaceType,
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

    private fun deriveSurfaceType(strip: RunwayStripEntity, grassCondition: GrassCondition): SleepvluchtSurfaceType =
        when (RunwaySurfaceType.valueOf(strip.surface)) {
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

    /** Fase 2c derivation — see [TakeoffViewModel.recalculate] for the general pattern; the
     * tow calculator's own headwind/surface/slope parameters mirror take-off's exactly, so the
     * derivation logic is identical, just routed through [TowPerformanceCalculator]. */
    private fun recalculate() {
        val s = _state.value

        val parsedMetar = s.selectedAirfield?.metarRaw
            ?.let { raw -> (MetarParser.parse(raw) as? MetarParseResult.Success)?.metar }

        val windDirection = parsedMetar?.windDirectionDeg
        val weatherDerivable = s.flightContextMode == FlightContextMode.AIRFIELD &&
            s.selectedAirfield != null && parsedMetar != null && windDirection != null
        val pressureAltDerivable = weatherDerivable && parsedMetar?.qnhHpa != null

        val effOat = if (!weatherDerivable || s.oatOverridden) s.oatC else parsedMetar!!.temperatureC.roundToInt()
        val effPressureAlt = if (!pressureAltDerivable || s.pressureAltOverridden) {
            s.pressureAltM
        } else {
            PressureAltitude.fromElevationAndQnh(s.selectedAirfield!!.elevationM, parsedMetar!!.qnhHpa!!).roundToInt()
        }

        val directions: List<RunwayDirectionOption> = s.runwayStrips.flatMap { it.directionOptions() }
        val advice: List<RunwayAdvice> = if (weatherDerivable && directions.isNotEmpty()) {
            RunwayAdvisor.advise(
                candidates = directions.map { it.toCandidate() },
                windDirectionDeg = windDirection!!,
                windSpeedKts = parsedMetar!!.windSpeedKts,
                demonstratedCrosswindKts = kmhToKts(performanceTow.limits.demonstratedCrosswindKmh),
                requiredDistanceM = { headwindKts, candidate ->
                    val direction = directions.first { it.designator == candidate.designator }
                    val surfaceType = deriveSurfaceType(direction.strip, s.grassCondition)
                    TowPerformanceCalculator.calculateTowTakeoff(
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
                    ).s2WithMarginM
                }
            )
        } else {
            emptyList()
        }

        val activeDirection = directions.find { it.designator == s.chosenRunwayDesignator }
            ?: directions.find { d -> advice.any { it.candidate.designator == d.designator && it.status == RunwayAdviceStatus.PREFERRED } }
        val activeAdvice = advice.find { it.candidate.designator == activeDirection?.designator }

        val effHeadwind = if (!weatherDerivable || s.headwindOverridden || activeAdvice == null) {
            s.headwindKts
        } else {
            activeAdvice.headwindKts.roundToInt()
        }
        val effSurface = if (!weatherDerivable || s.surfaceOverridden || activeDirection == null) {
            s.surfaceType
        } else {
            deriveSurfaceType(activeDirection.strip, s.grassCondition)
        }
        val effSlope = if (!weatherDerivable || s.slopeOverridden || activeDirection == null) {
            s.slopePct
        } else {
            activeDirection.slopePct.roundToInt()
        }

        val bundleChanged = s.effectiveOatC != effOat || s.effectivePressureAltM != effPressureAlt ||
            s.effectiveHeadwindKts != effHeadwind || s.effectiveSurfaceType != effSurface || s.effectiveSlopePct != effSlope
        val stillConfirmed = s.flightConfirmed && !bundleChanged

        val result = TowPerformanceCalculator.calculateTowTakeoff(
            performanceTow,
            corrections,
            sailplaneMassKg = s.sailplaneMassKg.toDouble(),
            ldRatio = if (s.ldRatioKnown) s.ldRatio.toDouble() else null,
            instructionFlight = s.instructionFlight,
            towplaneMassKg = s.effectiveTowplaneMassKg.toDouble(),
            oatC = effOat.toDouble(),
            pressureAltM = effPressureAlt.toDouble(),
            headwindKts = effHeadwind.toDouble(),
            slopePct = effSlope.toDouble(),
            marginFactor = s.marginFactorPct / 100.0,
            surfaceCorrectionFactor = surfaceFactorFor(effSurface)
        )

        _state.update {
            it.copy(
                result = result,
                runwayAdvice = advice,
                weatherDerivable = weatherDerivable,
                pressureAltDerivable = pressureAltDerivable,
                effectiveOatC = effOat,
                effectivePressureAltM = effPressureAlt,
                effectiveHeadwindKts = effHeadwind,
                effectiveSurfaceType = effSurface,
                effectiveSlopePct = effSlope,
                flightConfirmed = stillConfirmed
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
                        chosenRunwayDesignator = s.chosenRunwayDesignator
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
            sailplaneTypes: SailplaneTypesData
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return SleepvluchtViewModel(
                    repository, profileId, performanceTow, performanceNormal, corrections, sailplaneTypes
                ) as T
            }
        }
    }
}
