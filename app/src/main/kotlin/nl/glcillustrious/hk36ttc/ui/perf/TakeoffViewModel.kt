package nl.glcillustrious.hk36ttc.ui.perf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * Whole-number inputs matching the stepper UI. [marginFactorPct] is a percentage, seeded from
 * [PerformanceCorrectionsData.MarginFactorDefaults.takeoffDefaultFactor] rather than a literal
 * here — never a hardcoded default deeper than this ViewModel's starting state, and always
 * user-adjustable. [slopePct] follows "positive = uphill" (AIC P173 §5.5), default 0%.
 *
 * Fase 2c: in [FlightContextMode.AIRFIELD] mode, [oatC]/[pressureAltM]/[headwindKts]/
 * [surfaceType]/[slopePct] hold whatever was last typed while overridden or in Handmatig mode
 * — the values actually used for [result] are the `effective*` fields below, derived from the
 * selected airfield/runway/METAR unless a specific `*Overridden` flag says otherwise. This
 * split keeps a pilot's old manual entries intact if they switch back to Handmatig later.
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
    val chosenRunwayDesignator: String? = null,
    val flightConfirmed: Boolean = false,
    val oatOverridden: Boolean = false,
    val pressureAltOverridden: Boolean = false,
    val headwindOverridden: Boolean = false,
    val surfaceOverridden: Boolean = false,
    val slopeOverridden: Boolean = false,
    val runwayAdvice: List<RunwayAdvice> = emptyList(),
    /** True once an airfield with a usable (non-variable) METAR wind is selected — gates
     * whether OAT/headwind/surface/slope can be derived at all this calculation. */
    val weatherDerivable: Boolean = false,
    /** [weatherDerivable] plus the METAR actually having a QNH group. */
    val pressureAltDerivable: Boolean = false,
    val effectiveOatC: Int = 15,
    val effectivePressureAltM: Int = 0,
    val effectiveHeadwindKts: Int = 0,
    val effectiveSurfaceType: TakeoffSurfaceType = TakeoffSurfaceType.ASFALT,
    val effectiveSlopePct: Int = 0
) {
    val hasGrassRunway: Boolean
        get() = runwayStrips.any { RunwaySurfaceType.valueOf(it.surface) == RunwaySurfaceType.GRASS }
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

    val airfields: StateFlow<List<AirfieldEntity>> = repository.observeAirfields()
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
     * Fase 2c derivation: resolves OAT/pressure-altitude from the selected airfield's METAR,
     * then — if the METAR's wind has a usable direction — ranks every runway direction via
     * [RunwayAdvisor] and derives headwind/surface/slope from whichever direction is chosen or
     * (absent a choice) recommended. Any field the pilot has explicitly overridden, or that
     * can't be derived at all (no airfield, no METAR, variable wind), falls back to the plain
     * manual value. See rekenlogica.md §5/§8; known limitation: if the wind is unusable, the
     * whole airfield-derived bundle falls back to manual entry rather than partially deriving
     * only surface/slope from a manually-picked runway — a simplification for this round.
     */
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
                demonstratedCrosswindKts = kmhToKts(performanceNormal.demonstratedCrosswindKmh),
                requiredDistanceM = { headwindKts, candidate ->
                    val direction = directions.first { it.designator == candidate.designator }
                    val surfaceType = deriveSurfaceType(direction.strip, s.grassCondition)
                    PerformanceCalculator.calculateTakeoff(
                        performanceNormal, corrections,
                        oatC = effOat.toDouble(),
                        pressureAltM = effPressureAlt.toDouble(),
                        headwindKts = headwindKts,
                        surfaceFactor = surfaceFactorFor(surfaceType),
                        slopePct = candidate.slopePct,
                        marginFactor = s.marginFactorPct / 100.0
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

        val result = PerformanceCalculator.calculateTakeoff(
            performanceNormal, corrections,
            oatC = effOat.toDouble(),
            pressureAltM = effPressureAlt.toDouble(),
            headwindKts = effHeadwind.toDouble(),
            surfaceFactor = surfaceFactorFor(effSurface),
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
                effectiveHeadwindKts = effHeadwind,
                effectiveSurfaceType = effSurface,
                effectiveSlopePct = effSlope,
                flightConfirmed = stillConfirmed
            )
        }

        loadGuard.runIfLoaded {
            viewModelScope.launch {
                repository.saveTakeoffInput(
                    TakeoffInputEntity(
                        profileId, s.oatC, s.pressureAltM, s.headwindKts,
                        s.surfaceType.name, s.slopePct, s.marginFactorPct, s.chosenRunwayDesignator
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
