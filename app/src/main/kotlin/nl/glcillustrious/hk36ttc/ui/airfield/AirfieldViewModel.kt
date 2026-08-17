package nl.glcillustrious.hk36ttc.ui.airfield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.core.metar.MetarParseResult
import nl.glcillustrious.hk36ttc.core.metar.MetarParser
import nl.glcillustrious.hk36ttc.data.catalog.AirfieldRefreshResult
import nl.glcillustrious.hk36ttc.data.catalog.AirportCatalogRepository
import nl.glcillustrious.hk36ttc.data.catalog.RunwayImportResult
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.RunwaySurfaceType
import nl.glcillustrious.hk36ttc.data.metar.MetarFetchResult
import nl.glcillustrious.hk36ttc.data.metar.MetarRepository
import nl.glcillustrious.hk36ttc.data.metar.MetarRepository.Companion.stationCode

data class AirfieldRow(
    val airfield: AirfieldEntity,
    val isFavorite: Boolean
)

class AirfieldListViewModel(
    private val repository: AircraftProfileRepository,
    private val catalog: AirportCatalogRepository
) : ViewModel() {

    val rows: StateFlow<List<AirfieldRow>> = combine(
        repository.observeAirfields(),
        repository.observeFavoriteAirfieldIds()
    ) { airfields, favoriteIds ->
        val favoriteSet = favoriteIds.toSet()
        airfields.map { AirfieldRow(it, isFavorite = favoriteSet.contains(it.id)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Result of the last "update from source" tap, for the screen to report; cleared once shown. */
    private val _refreshResult = MutableStateFlow<AirfieldRefreshResult?>(null)
    val refreshResult: StateFlow<AirfieldRefreshResult?> = _refreshResult

    fun deleteAirfield(airfield: AirfieldEntity) {
        viewModelScope.launch { repository.deleteAirfieldCascade(airfield) }
    }

    fun toggleFavorite(row: AirfieldRow) {
        viewModelScope.launch { repository.setAirfieldFavorite(row.airfield.id, !row.isFavorite) }
    }

    /**
     * Refreshes the pilot's own airfields against the catalogue — name and elevation only.
     * Runways and METAR data are not touched; see
     * [AirportCatalogRepository.updateAirfieldsFromCatalog].
     */
    fun updateFromCatalog() {
        viewModelScope.launch {
            catalog.seedFromAssetsIfEmpty()
            _refreshResult.value = catalog.updateAirfieldsFromCatalog(rows.value.map { it.airfield })
        }
    }

    fun clearRefreshResult() {
        _refreshResult.value = null
    }

    companion object {
        fun factory(repository: AircraftProfileRepository, catalog: AirportCatalogRepository) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return AirfieldListViewModel(repository, catalog) as T
                }
            }
    }
}

/** [parsedMetar] is null only when [metarRaw] is blank — otherwise a fresh parse attempt on
 * every keystroke, so the screen can show a live summary or a specific error immediately,
 * before the pilot ever taps save. */
data class AirfieldFormState(
    val id: Long = 0,
    val name: String = "",
    val icao: String = "",
    val metarStationIcao: String = "",
    val elevationM: Int = 0,
    val metarRaw: String = "",
    val metarEnteredAtEpochMs: Long? = null,
    /** True once a save has been attempted while [name] was blank — pressing "opslaan" on an
     * empty name used to just silently do nothing, which read as a broken button rather than a
     * validation error. Drives [nameError] instead of showing an error on a screen the pilot
     * hasn't touched yet. */
    val saveAttempted: Boolean = false
) {
    val parsedMetar: MetarParseResult?
        get() = metarRaw.takeIf { it.isNotBlank() }?.let { MetarParser.parse(it) }

    val nameError: Boolean
        get() = saveAttempted && name.isBlank()
}

data class RunwayStripFormState(
    val id: Long = 0,
    val designatorA: String = "",
    val designatorB: String = "",
    val headingDegTrueA: Int = 0,
    val lengthM: Int = 800,
    val surface: RunwaySurfaceType = RunwaySurfaceType.ASPHALT,
    val slopePctA: Int = 0,
    val oneWay: Boolean = false
)

fun RunwayStripEntity.toFormState() = RunwayStripFormState(
    id = id,
    designatorA = designatorA,
    designatorB = designatorB,
    headingDegTrueA = headingDegTrueA.roundToInt(),
    lengthM = lengthM.roundToInt(),
    surface = RunwaySurfaceType.valueOf(surface),
    slopePctA = slopePctA.roundToInt(),
    oneWay = oneWay
)

/**
 * Backs both the "new airfield" and "edit airfield" flows. Runway strips are managed
 * independently of the airfield-metadata save button (added/edited/deleted immediately, same
 * as registrations in `ProfileListScreen`) — but only once the airfield itself has a real id,
 * since a strip needs a parent to belong to.
 */
class AirfieldEditViewModel(
    private val repository: AircraftProfileRepository,
    private val catalog: AirportCatalogRepository,
    private val metarRepository: MetarRepository,
    private val metarConfig: MetarConfigData,
    airfieldId: Long
) : ViewModel() {

    private val _state = MutableStateFlow(AirfieldFormState(id = airfieldId))
    val state: StateFlow<AirfieldFormState> = _state

    private val _runways = MutableStateFlow<List<RunwayStripEntity>>(emptyList())
    val runways: StateFlow<List<RunwayStripEntity>> = _runways

    /** Result of the last "import runways from source" tap, for the screen to report. Cleared
     * by [clearRunwayImportResult] once shown. */
    private val _runwayImportResult = MutableStateFlow<RunwayImportResult?>(null)
    val runwayImportResult: StateFlow<RunwayImportResult?> = _runwayImportResult

    /** True when this airfield's ICAO code is known to the OurAirports catalogue, so offering
     * to import its runways makes sense at all. */
    private val _catalogIdent = MutableStateFlow<String?>(null)
    val catalogIdent: StateFlow<String?> = _catalogIdent

    /** True while an online METAR lookup is in flight, so the button can show it's working. */
    private val _metarFetching = MutableStateFlow(false)
    val metarFetching: StateFlow<Boolean> = _metarFetching

    /** Outcome of the last lookup. Shown inline under the weather summary — only when it says
     * something useful, i.e. a failure or a station with no observation. */
    private val _metarFetchResult = MutableStateFlow<MetarFetchResult?>(null)
    val metarFetchResult: StateFlow<MetarFetchResult?> = _metarFetchResult

    init {
        if (airfieldId != 0L) {
            viewModelScope.launch {
                repository.getAirfield(airfieldId)?.let { airfield ->
                    _state.update {
                        it.copy(
                            name = airfield.name,
                            icao = airfield.icao.orEmpty(),
                            metarStationIcao = airfield.metarStationIcao.orEmpty(),
                            elevationM = airfield.elevationM.roundToInt(),
                            metarRaw = airfield.metarRaw.orEmpty(),
                            metarEnteredAtEpochMs = airfield.metarEnteredAtEpochMs
                        )
                    }
                }
                refreshRunways(airfieldId)
                refreshCatalogIdent()
                fetchMetarIfStationChanged()
            }
        }
    }

    fun update(transform: (AirfieldFormState) -> AirfieldFormState) {
        _state.update(transform)
    }

    fun saveAirfieldInfo() {
        if (_state.value.name.isBlank()) {
            // Surface it instead of doing nothing — a blank-name save used to just silently
            // fail, which reads as a broken button rather than "fill this in first".
            _state.update { it.copy(saveAttempted = true) }
            return
        }
        viewModelScope.launch { saveAirfieldInfoAndWait() }
    }

    /** The body of [saveAirfieldInfo], suspending, so [fetchMetar] can be sure the row exists
     * (and carries the station code just typed) before looking anything up for it. */
    private suspend fun saveAirfieldInfoAndWait() {
        val s = _state.value
        if (s.name.isBlank()) return
        // Only stamp a new "fetched/entered at" when the report text actually changed. Saving an
        // unrelated edit (a corrected name, say) must not make a stale METAR look freshly
        // obtained, since that stamp is what decides when the app fetches by itself.
        val storedRaw = if (s.id == 0L) null else repository.getAirfield(s.id)?.metarRaw
        val enteredAt = when {
            s.metarRaw.isBlank() -> null
            s.metarRaw == storedRaw -> s.metarEnteredAtEpochMs ?: System.currentTimeMillis()
            else -> System.currentTimeMillis()
        }
        val savedId = repository.saveAirfield(
            AirfieldEntity(
                id = s.id,
                name = s.name.trim(),
                icao = s.icao.trim().uppercase().ifBlank { null },
                metarStationIcao = s.metarStationIcao.trim().uppercase().ifBlank { null },
                elevationM = s.elevationM.toDouble(),
                metarRaw = s.metarRaw.ifBlank { null },
                metarEnteredAtEpochMs = enteredAt
            )
        )
        _state.update { it.copy(id = savedId, metarEnteredAtEpochMs = enteredAt) }
        refreshRunways(savedId)
        refreshCatalogIdent()
        // Saving is when a newly typed METAR station becomes real, so this is the point to go
        // and read that station's weather. Does nothing when the station is unchanged.
        fetchMetarIfStationChanged()
    }

    /**
     * Pulls this airfield's runways from the OurAirports catalogue.
     *
     * The guard that matters lives in
     * [AirportCatalogRepository.importRunwaysForAirfield]: an airfield that already has runways
     * is refused outright, so nothing the pilot entered is ever replaced or merged with.
     */
    fun importRunwaysFromCatalog() {
        val airfieldId = _state.value.id
        if (airfieldId == 0L) return
        viewModelScope.launch {
            val ident = _catalogIdent.value ?: catalog.catalogIdentFor(currentAirfield()) ?: run {
                _runwayImportResult.value = RunwayImportResult.NothingAvailable
                return@launch
            }
            _runwayImportResult.value = catalog.importRunwaysForAirfield(airfieldId, ident)
            refreshRunways(airfieldId)
        }
    }

    fun clearRunwayImportResult() {
        _runwayImportResult.value = null
    }

    /**
     * Fetches this airfield's METAR and shows what came back. There is no button for this: the
     * pilot never types or pastes METAR text, so the screen just goes and gets the latest on
     * open, and again whenever the station it should read is changed.
     *
     * A failed lookup leaves whatever is already stored alone — see [MetarRepository] — so the
     * previously fetched report stays usable offline.
     */
    private fun fetchMetar() {
        val current = _state.value
        if (current.name.isBlank() || current.id == 0L || _metarFetching.value) return
        viewModelScope.launch {
            _metarFetching.value = true
            _metarFetchResult.value = null
            try {
                val airfield = repository.getAirfield(current.id) ?: return@launch
                _metarFetchResult.value = metarRepository.refreshOne(airfield, metarConfig)
                repository.getAirfield(current.id)?.let { updated ->
                    _state.update {
                        it.copy(
                            metarRaw = updated.metarRaw.orEmpty(),
                            metarEnteredAtEpochMs = updated.metarEnteredAtEpochMs
                        )
                    }
                }
            } finally {
                _metarFetching.value = false
            }
        }
    }

    /**
     * The station this airfield's weather was last fetched for, so a changed station code
     * triggers a new lookup while unrelated edits (a corrected name) do not.
     */
    private var lastFetchedStation: String? = null

    /** Fetches only when the station to read actually changed since the last lookup. */
    private fun fetchMetarIfStationChanged() {
        val station = currentAirfield().stationCode() ?: return
        if (station == lastFetchedStation) return
        lastFetchedStation = station
        fetchMetar()
    }

    /** The form as an entity, for lookups that only read fields (catalogue match, station
     * code) — never for saving, which goes through [saveAirfieldInfoAndWait]. */
    private fun currentAirfield(): AirfieldEntity {
        val s = _state.value
        return AirfieldEntity(
            id = s.id,
            name = s.name,
            icao = s.icao.trim().uppercase().ifBlank { null },
            metarStationIcao = s.metarStationIcao.trim().uppercase().ifBlank { null },
            elevationM = s.elevationM.toDouble(),
            metarRaw = s.metarRaw.ifBlank { null },
            metarEnteredAtEpochMs = s.metarEnteredAtEpochMs
        )
    }

    private suspend fun refreshCatalogIdent() {
        _catalogIdent.value = catalog.catalogIdentFor(currentAirfield())
    }

    fun saveRunway(form: RunwayStripFormState) {
        val airfieldId = _state.value.id
        if (airfieldId == 0L) return
        viewModelScope.launch {
            repository.saveRunwayStrip(
                RunwayStripEntity(
                    id = form.id,
                    airfieldId = airfieldId,
                    designatorA = form.designatorA.trim(),
                    designatorB = if (form.oneWay) "" else form.designatorB.trim(),
                    headingDegTrueA = form.headingDegTrueA.toDouble(),
                    lengthM = form.lengthM.toDouble(),
                    surface = form.surface.name,
                    slopePctA = form.slopePctA.toDouble(),
                    oneWay = form.oneWay
                )
            )
            refreshRunways(airfieldId)
        }
    }

    fun deleteRunway(strip: RunwayStripEntity) {
        viewModelScope.launch {
            repository.deleteRunwayStrip(strip)
            refreshRunways(strip.airfieldId)
        }
    }

    private suspend fun refreshRunways(airfieldId: Long) {
        _runways.value = if (airfieldId == 0L) emptyList() else repository.getRunwayStrips(airfieldId)
    }

    companion object {
        fun factory(
            repository: AircraftProfileRepository,
            catalog: AirportCatalogRepository,
            metarRepository: MetarRepository,
            metarConfig: MetarConfigData,
            airfieldId: Long
        ) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return AirfieldEditViewModel(repository, catalog, metarRepository, metarConfig, airfieldId) as T
            }
        }
    }
}
