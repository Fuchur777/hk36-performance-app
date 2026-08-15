package nl.glcillustrious.hk36ttc.ui.perf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCalculator
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.core.perf.TakeoffResult
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.TakeoffInputEntity

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
 */
data class TakeoffFormState(
    val registration: String? = null,
    val oatC: Int = 15,
    val pressureAltM: Int = 0,
    val headwindKts: Int = 0,
    val surfaceType: TakeoffSurfaceType = TakeoffSurfaceType.ASFALT,
    val slopePct: Int = 0,
    val marginFactorPct: Int = 133,
    val result: TakeoffResult? = null
)

class TakeoffViewModel(
    repository: AircraftProfileRepository,
    profileId: Long,
    private val performanceNormal: PerformanceNormalData,
    private val corrections: PerformanceCorrectionsData
) : ViewModel() {

    val marginFactorDefaultPct: Int = (corrections.marginFactorDefaults.takeoffDefaultFactor * 100).roundToInt()

    private val _state = MutableStateFlow(TakeoffFormState(marginFactorPct = marginFactorDefaultPct))
    val state: StateFlow<TakeoffFormState> = _state

    init {
        viewModelScope.launch {
            val entity = repository.getById(profileId)
            val savedInput = repository.getTakeoffInput(profileId)
            _state.update {
                if (savedInput == null) {
                    it.copy(registration = entity?.registration)
                } else {
                    it.copy(
                        registration = entity?.registration,
                        oatC = savedInput.oatC,
                        pressureAltM = savedInput.pressureAltM,
                        headwindKts = savedInput.headwindKts,
                        surfaceType = TakeoffSurfaceType.valueOf(savedInput.surfaceType),
                        slopePct = savedInput.slopePct,
                        marginFactorPct = savedInput.marginFactorPct
                    )
                }
            }
            recalculate()
        }
    }

    fun update(transform: (TakeoffFormState) -> TakeoffFormState) {
        _state.update(transform)
        recalculate()
    }

    private fun recalculate() {
        val s = _state.value
        val surfaceFactor = when (s.surfaceType) {
            TakeoffSurfaceType.ASFALT -> 1.0
            TakeoffSurfaceType.DROOG_GRAS -> 1.0 + performanceNormal.takeoff.grassRunwayPenaltyMinPct / 100.0
            TakeoffSurfaceType.NAT_GRAS -> corrections.grassTakeoffFactors.wetGrassFactor
            TakeoffSurfaceType.ZACHTE_GROND -> corrections.grassTakeoffFactors.softGroundFactor
        }
        val result = PerformanceCalculator.calculateTakeoff(
            performanceNormal,
            corrections,
            oatC = s.oatC.toDouble(),
            pressureAltM = s.pressureAltM.toDouble(),
            headwindKts = s.headwindKts.toDouble(),
            surfaceFactor = surfaceFactor,
            slopePct = s.slopePct.toDouble(),
            marginFactor = s.marginFactorPct / 100.0
        )
        _state.update { it.copy(result = result) }

        viewModelScope.launch {
            repository.saveTakeoffInput(
                TakeoffInputEntity(
                    profileId, s.oatC, s.pressureAltM, s.headwindKts,
                    s.surfaceType.name, s.slopePct, s.marginFactorPct
                )
            )
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
