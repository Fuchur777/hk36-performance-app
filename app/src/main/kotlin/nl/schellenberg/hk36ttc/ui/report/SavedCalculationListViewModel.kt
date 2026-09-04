package nl.schellenberg.hk36ttc.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.SavedCalculationEntity
import nl.schellenberg.hk36ttc.data.local.SavedCalculationType

/** Backs [SavedCalculationListScreen] — same shape as `RealLifeLogListViewModel`, its closest
 * relative. [registration] is loaded here (rather than passed in from the caller) purely so the
 * navigation route only needs a `profileId` + [type], the same two arguments every other
 * per-registration route already carries. */
class SavedCalculationListViewModel(
    private val repository: AircraftProfileRepository,
    profileId: Long,
    val type: SavedCalculationType
) : ViewModel() {

    private val _registration = MutableStateFlow<String?>(null)
    val registration: StateFlow<String?> = _registration

    val calculations: StateFlow<List<SavedCalculationEntity>> = repository.observeSavedCalculations(profileId, type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _registration.value = repository.getById(profileId)?.registration
        }
    }

    fun deleteCalculation(calculation: SavedCalculationEntity) {
        viewModelScope.launch { repository.deleteCalculation(calculation) }
    }

    companion object {
        fun factory(repository: AircraftProfileRepository, profileId: Long, type: SavedCalculationType) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return SavedCalculationListViewModel(repository, profileId, type) as T
                }
            }
    }
}
