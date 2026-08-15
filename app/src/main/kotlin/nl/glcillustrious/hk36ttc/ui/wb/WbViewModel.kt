package nl.glcillustrious.hk36ttc.ui.wb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.glcillustrious.hk36ttc.core.wb.AircraftProfile
import nl.glcillustrious.hk36ttc.core.wb.WBCalculator
import nl.glcillustrious.hk36ttc.core.wb.WBInput
import nl.glcillustrious.hk36ttc.core.wb.WBResult
import nl.glcillustrious.hk36ttc.core.wb.WbConstantsData
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.toDomain

/**
 * Whole-number inputs, matching the stepper UI. `copilotKg = 0` IS the "solo flight" signal
 * (WBCalculator already treats `copilotKg <= 0.0` as solo) — there's no separate blank state
 * to track now that this isn't a free-text field.
 */
data class WbFormState(
    val profile: AircraftProfile? = null,
    val profileNotFound: Boolean = false,
    val pilotKg: Int = 75,
    val copilotKg: Int = 0,
    val fuelLiters: Int = 0,
    val baggageKg: Int = 0,
    val result: WBResult? = null
)

class WbViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    private val wbConstants: WbConstantsData
) : ViewModel() {

    private val _state = MutableStateFlow(WbFormState())
    val state: StateFlow<WbFormState> = _state

    init {
        viewModelScope.launch {
            val entity = repository.getById(profileId)
            if (entity == null) {
                _state.update { it.copy(profileNotFound = true) }
            } else {
                _state.update { it.copy(profile = entity.toDomain()) }
                recalculate()
            }
        }
    }

    fun updatePilot(value: Int) = update { it.copy(pilotKg = value) }
    fun updateCopilot(value: Int) = update { it.copy(copilotKg = value) }
    fun updateFuelLiters(value: Int) = update { it.copy(fuelLiters = value) }
    fun updateBaggage(value: Int) = update { it.copy(baggageKg = value) }

    private fun update(transform: (WbFormState) -> WbFormState) {
        _state.update(transform)
        recalculate()
    }

    private fun recalculate() {
        val current = _state.value
        val profile = current.profile ?: return

        val result = WBCalculator.calculate(
            profile,
            WBInput(
                pilotKg = current.pilotKg.toDouble(),
                copilotKg = current.copilotKg.toDouble(),
                fuelKg = wbConstants.fuelKgFromLiters(current.fuelLiters.toDouble()),
                baggageKg = current.baggageKg.toDouble()
            ),
            wbConstants
        )
        _state.update { it.copy(result = result) }

        // Cached so other modules (sleepvlucht: "sleepvliegtuig-gewicht") can default to
        // this aircraft's actual current weight instead of a separately-entered guess.
        viewModelScope.launch {
            repository.saveLastWbResult(profileId, result.totalMassKg)
        }
    }

    companion object {
        fun factory(repository: AircraftProfileRepository, profileId: Long, wbConstants: WbConstantsData) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return WbViewModel(repository, profileId, wbConstants) as T
                }
            }
    }
}
