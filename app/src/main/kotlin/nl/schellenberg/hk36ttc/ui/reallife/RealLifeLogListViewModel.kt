package nl.schellenberg.hk36ttc.ui.reallife

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity

class RealLifeLogListViewModel(
    private val repository: AircraftProfileRepository,
    profileId: Long
) : ViewModel() {

    val logs: StateFlow<List<RealLifeLogEntity>> = repository.observeRealLifeLogs(profileId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteLog(log: RealLifeLogEntity) {
        viewModelScope.launch { repository.deleteRealLifeLogCascade(log) }
    }

    companion object {
        fun factory(repository: AircraftProfileRepository, profileId: Long) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return RealLifeLogListViewModel(repository, profileId) as T
                }
            }
    }
}
