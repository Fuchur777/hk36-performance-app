package nl.glcillustrious.hk36ttc.ui.airfield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.glcillustrious.hk36ttc.data.catalog.AirportCatalogEntity
import nl.glcillustrious.hk36ttc.data.catalog.AirportCatalogRepository
import nl.glcillustrious.hk36ttc.data.catalog.CatalogMetaEntity

/** What the catalogue screen is currently doing, so the UI can show progress or an error. */
sealed interface CatalogStatus {
    data object Ready : CatalogStatus

    /** First-run seeding from the bundled asset, or a network refresh. */
    data object Loading : CatalogStatus

    /** A refresh failed — [message] is already localized by the caller. */
    data class Failed(val message: String) : CatalogStatus
}

data class AirportCatalogUiState(
    val query: String = "",
    val status: CatalogStatus = CatalogStatus.Loading,
    val meta: CatalogMetaEntity? = null
)

/**
 * Search over the ~72,000-entry OurAirports catalogue.
 *
 * Deliberately unlike [nl.glcillustrious.hk36ttc.ui.sailplane.SailplaneTypesViewModel], which
 * filters a 192-item in-memory list on every keystroke: at this size the query has to run in
 * SQL with a LIMIT, and the keystrokes have to be debounced. docs/00-plan.md §13 asked for
 * exactly this — a faster filter than the sailplane screen's, with ICAO-code search leading.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AirportCatalogViewModel(
    private val catalog: AirportCatalogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AirportCatalogUiState())
    val state: StateFlow<AirportCatalogUiState> = _state

    private val query = MutableStateFlow("")

    /** Bumped after a refresh so the visible results re-run against the new catalogue. A
     * StateFlow drops a repeated identical value, so re-assigning [query] would not do it. */
    private val reloadTrigger = MutableStateFlow(0)

    val results: StateFlow<List<AirportCatalogEntity>> = combine(
        // Long enough that a typed "EHGR" runs one query rather than four, short enough that
        // it still feels immediate.
        query.debounce(250),
        reloadTrigger
    ) { text, _ -> text }
        .mapLatest { catalog.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Seeds from the bundled asset the first time this screen is ever opened; a no-op
            // on every later visit.
            catalog.seedFromAssetsIfEmpty()
            _state.update { it.copy(status = CatalogStatus.Ready, meta = catalog.meta()) }
        }
    }

    fun updateQuery(value: String) {
        _state.update { it.copy(query = value) }
        query.value = value
    }

    /**
     * Pulls today's catalogue from OurAirports. [networkErrorMessage] is passed in already
     * localized because a ViewModel has no resources — the screen owns the string.
     */
    fun refresh(networkErrorMessage: String) {
        _state.update { it.copy(status = CatalogStatus.Loading) }
        viewModelScope.launch {
            val status = try {
                catalog.refreshFromNetwork()
                CatalogStatus.Ready
            } catch (e: Exception) {
                // Any failure (offline, DNS, HTTP error, malformed file) leaves the existing
                // catalogue untouched — see AirportCatalogRepository.refreshFromNetwork.
                CatalogStatus.Failed(e.message?.let { "$networkErrorMessage ($it)" } ?: networkErrorMessage)
            }
            _state.update { it.copy(status = status, meta = catalog.meta()) }
            // Re-run the current query against whatever is now stored.
            reloadTrigger.update { it + 1 }
        }
    }

    /** Creates one of the pilot's own airfields from [entry] and returns its new id. */
    suspend fun addAirfield(entry: AirportCatalogEntity): Long = catalog.createAirfieldFrom(entry)

    companion object {
        fun factory(catalog: AirportCatalogRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return AirportCatalogViewModel(catalog) as T
            }
        }
    }
}
