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
import nl.glcillustrious.hk36ttc.core.metar.MetarParseResult
import nl.glcillustrious.hk36ttc.core.metar.MetarParser
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.RunwaySurfaceType

data class AirfieldRow(
    val airfield: AirfieldEntity,
    val isFavorite: Boolean
)

class AirfieldListViewModel(private val repository: AircraftProfileRepository) : ViewModel() {

    val rows: StateFlow<List<AirfieldRow>> = combine(
        repository.observeAirfields(),
        repository.observeFavoriteAirfieldIds()
    ) { airfields, favoriteIds ->
        val favoriteSet = favoriteIds.toSet()
        airfields.map { AirfieldRow(it, isFavorite = favoriteSet.contains(it.id)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteAirfield(airfield: AirfieldEntity) {
        viewModelScope.launch { repository.deleteAirfieldCascade(airfield) }
    }

    fun toggleFavorite(row: AirfieldRow) {
        viewModelScope.launch { repository.setAirfieldFavorite(row.airfield.id, !row.isFavorite) }
    }

    companion object {
        fun factory(repository: AircraftProfileRepository) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return AirfieldListViewModel(repository) as T
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
    val metarEnteredAtEpochMs: Long? = null
) {
    val parsedMetar: MetarParseResult?
        get() = metarRaw.takeIf { it.isNotBlank() }?.let { MetarParser.parse(it) }
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
    airfieldId: Long
) : ViewModel() {

    private val _state = MutableStateFlow(AirfieldFormState(id = airfieldId))
    val state: StateFlow<AirfieldFormState> = _state

    private val _runways = MutableStateFlow<List<RunwayStripEntity>>(emptyList())
    val runways: StateFlow<List<RunwayStripEntity>> = _runways

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
            }
        }
    }

    fun update(transform: (AirfieldFormState) -> AirfieldFormState) {
        _state.update(transform)
    }

    fun saveAirfieldInfo() {
        val s = _state.value
        if (s.name.isBlank()) return
        viewModelScope.launch {
            val enteredAt = if (s.metarRaw.isBlank()) null else System.currentTimeMillis()
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
        }
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
        fun factory(repository: AircraftProfileRepository, airfieldId: Long) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return AirfieldEditViewModel(repository, airfieldId) as T
                }
            }
    }
}
