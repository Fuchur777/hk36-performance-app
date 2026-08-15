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
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceTowData
import nl.glcillustrious.hk36ttc.core.perf.SailplaneTypesData
import nl.glcillustrious.hk36ttc.core.perf.TowClassSelectionResult
import nl.glcillustrious.hk36ttc.core.perf.TowPerformanceCalculator
import nl.glcillustrious.hk36ttc.core.perf.TowTakeoffResult
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.SleepvluchtInputEntity

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
    val result: TowTakeoffResult? = null
) {
    /** The weight actually used for the calculation: the W&B value unless there isn't one
     * yet, or the user has explicitly chosen to override it. */
    val effectiveTowplaneMassKg: Int
        get() = if (towplaneMassManualOverride || towplaneMassFromWbKg == null) {
            towplaneMassManualKg
        } else {
            towplaneMassFromWbKg
        }
}

class SleepvluchtViewModel(
    private val repository: AircraftProfileRepository,
    profileId: Long,
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

    init {
        viewModelScope.launch {
            val entity = repository.getById(profileId)
            val lastWb = repository.getLastWbResult(profileId)
            val savedInput = repository.getSleepvluchtInput(profileId)
            _state.update {
                if (savedInput == null) {
                    it.copy(
                        registration = entity?.registration,
                        towplaneMassFromWbKg = lastWb?.totalMassKg?.roundToInt()
                    )
                } else {
                    it.copy(
                        registration = entity?.registration,
                        towplaneMassFromWbKg = lastWb?.totalMassKg?.roundToInt(),
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
            }
            recalculate()
        }
    }

    fun update(transform: (SleepvluchtFormState) -> SleepvluchtFormState) {
        _state.update(transform)
        recalculate()
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

    private fun recalculate() {
        val s = _state.value
        val result = TowPerformanceCalculator.calculateTowTakeoff(
            performanceTow,
            corrections,
            sailplaneMassKg = s.sailplaneMassKg.toDouble(),
            ldRatio = if (s.ldRatioKnown) s.ldRatio.toDouble() else null,
            instructionFlight = s.instructionFlight,
            towplaneMassKg = s.effectiveTowplaneMassKg.toDouble(),
            oatC = s.oatC.toDouble(),
            pressureAltM = s.pressureAltM.toDouble(),
            headwindKts = s.headwindKts.toDouble(),
            slopePct = s.slopePct.toDouble(),
            marginFactor = s.marginFactorPct / 100.0,
            surfaceCorrectionFactor = surfaceFactorFor(s.surfaceType)
        )
        _state.update { it.copy(result = result) }

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
                    towplaneMassManualKg = s.towplaneMassManualKg
                )
            )
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
