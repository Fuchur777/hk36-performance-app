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
import nl.glcillustrious.hk36ttc.core.perf.LandingResult
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCalculator
import nl.glcillustrious.hk36ttc.core.perf.PerformanceCorrectionsData
import nl.glcillustrious.hk36ttc.core.perf.PerformanceNormalData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.LandingInputEntity

/** Supplement 11 publishes no landing correction for anything but a paved runway — the grass
 * factors come from AIC P173 instead, and "Aangepast" lets the pilot enter their own estimate
 * up to the AIC P173-published upper bound for very short, smooth grass. */
enum class LandingSurfaceType { ASFALT, DROOG_GRAS, NAT_GRAS, AANGEPAST }

/** [slopePct] follows "positive = uphill", default 0% (AIC P173 §7.5 — only downhill is
 * adverse for landing). [marginFactorPct] is seeded from
 * [PerformanceCorrectionsData.MarginFactorDefaults.landingDefaultFactor], never a hardcoded
 * literal here. */
data class LandingFormState(
    val registration: String? = null,
    val oatC: Int = 15,
    val pressureAltM: Int = 0,
    val surfaceType: LandingSurfaceType = LandingSurfaceType.ASFALT,
    val customSurfaceFactorPct: Int = 115,
    val slopePct: Int = 0,
    val marginFactorPct: Int = 143,
    val result: LandingResult? = null
)

class LandingViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    private val performanceNormal: PerformanceNormalData,
    private val corrections: PerformanceCorrectionsData
) : ViewModel() {

    val marginFactorDefaultPct: Int = (corrections.marginFactorDefaults.landingDefaultFactor * 100).roundToInt()

    private val _state = MutableStateFlow(LandingFormState(marginFactorPct = marginFactorDefaultPct))
    val state: StateFlow<LandingFormState> = _state

    init {
        viewModelScope.launch {
            val entity = repository.getById(profileId)
            val savedInput = repository.getLandingInput(profileId)
            _state.update {
                if (savedInput == null) {
                    it.copy(registration = entity?.registration)
                } else {
                    it.copy(
                        registration = entity?.registration,
                        oatC = savedInput.oatC,
                        pressureAltM = savedInput.pressureAltM,
                        surfaceType = LandingSurfaceType.valueOf(savedInput.surfaceType),
                        customSurfaceFactorPct = savedInput.customSurfaceFactorPct,
                        slopePct = savedInput.slopePct,
                        marginFactorPct = savedInput.marginFactorPct
                    )
                }
            }
            recalculate()
        }
    }

    fun update(transform: (LandingFormState) -> LandingFormState) {
        _state.update(transform)
        recalculate()
    }

    private fun recalculate() {
        val s = _state.value
        val surfaceFactor = when (s.surfaceType) {
            LandingSurfaceType.ASFALT -> 1.0
            LandingSurfaceType.DROOG_GRAS -> corrections.grassLandingFactors.dryGrassFactor
            LandingSurfaceType.NAT_GRAS -> corrections.grassLandingFactors.wetGrassFactor
            LandingSurfaceType.AANGEPAST -> s.customSurfaceFactorPct / 100.0
        }
        val result = PerformanceCalculator.calculateLanding(
            performanceNormal,
            corrections,
            oatC = s.oatC.toDouble(),
            pressureAltM = s.pressureAltM.toDouble(),
            surfaceFactor = surfaceFactor,
            slopePct = s.slopePct.toDouble(),
            marginFactor = s.marginFactorPct / 100.0
        )
        _state.update { it.copy(result = result) }

        viewModelScope.launch {
            repository.saveLandingInput(
                LandingInputEntity(
                    profileId, s.oatC, s.pressureAltM, s.surfaceType.name,
                    s.customSurfaceFactorPct, s.slopePct, s.marginFactorPct
                )
            )
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
