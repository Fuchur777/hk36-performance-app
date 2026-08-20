package nl.schellenberg.hk36ttc.ui.sailplane

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.schellenberg.hk36ttc.core.perf.SailplaneTypesData
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository

data class SailplaneTypeRow(
    val type: SailplaneTypesData.SailplaneType,
    val isFavorite: Boolean
)

class SailplaneTypesViewModel(
    private val repository: AircraftProfileRepository,
    private val sailplaneTypes: SailplaneTypesData
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val rows: StateFlow<List<SailplaneTypeRow>> = combine(
        _searchQuery,
        repository.observeFavoriteSailplaneTypeNames()
    ) { query, favoriteNames ->
        val favoriteSet = favoriteNames.toSet()
        sailplaneTypes.types
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .map { SailplaneTypeRow(it, isFavorite = favoriteSet.contains(it.name)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(row: SailplaneTypeRow) {
        viewModelScope.launch {
            repository.setSailplaneTypeFavorite(row.type.name, !row.isFavorite)
        }
    }

    companion object {
        fun factory(repository: AircraftProfileRepository, sailplaneTypes: SailplaneTypesData) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return SailplaneTypesViewModel(repository, sailplaneTypes) as T
                }
            }
    }
}
