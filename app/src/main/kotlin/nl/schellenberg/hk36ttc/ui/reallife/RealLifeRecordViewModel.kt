package nl.schellenberg.hk36ttc.ui.reallife

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.schellenberg.hk36ttc.data.local.AircraftProfileRepository
import nl.schellenberg.hk36ttc.data.local.BarometerSampleEntity
import nl.schellenberg.hk36ttc.data.local.ImuSampleEntity
import nl.schellenberg.hk36ttc.data.local.LocationSampleEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeConfiguration
import nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeMarkerEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeMarkerType
import nl.schellenberg.hk36ttc.data.local.RealLifeStopReason

/**
 * Stays [androidx.compose.ui.platform.LocalContext]-free like every other ViewModel in this app —
 * it never touches [RealLifeRecorder] directly, only receives already-batched sample lists from
 * it (via [onLocationBatch]/[onImuBatch]/[onBarometerBatch], wired up in `RealLifeRecordScreen.kt`)
 * and persists them. Owns the recording-session policy: what state a recording is in, and the
 * safety timeout — the recorder itself is a dumb sensor/location pump with no opinion on when a
 * session should end.
 */
data class RealLifeRecordState(
    val isRecording: Boolean = false,
    /** True from the moment [RealLifeRecordViewModel.startRecording] is called until the log row
     * insert completes and [isRecording] flips true (or the attempt fails) -- checked
     * synchronously, before any suspend point, so a rapid double-tap on Start can't launch two
     * overlapping recordings while the first insert is still in flight. */
    val starting: Boolean = false,
    val configuration: RealLifeConfiguration = RealLifeConfiguration.NORMAL,
    val notes: String = "",
    val startedAtEpochMs: Long? = null,
    val currentLogId: Long? = null,
    val locationCount: Int = 0,
    val imuCount: Int = 0,
    val barometerCount: Int = 0,
    /** Set by [RealLifeRecordViewModel.recordingStartFailed] when [RealLifeRecorder.start] never
     * actually registered a listener (e.g. permission revoked between the gate and the tap) --
     * surfaced to the pilot instead of leaving [isRecording] stuck true with no explanation. */
    val startError: String? = null,
    val lastStopReason: RealLifeStopReason? = null,
    /** Which ground-truth markers (see [RealLifeMarkerType]) have already been tapped this
     * recording — lets the UI show a marker button as "done" rather than inviting a duplicate
     * tap for the same event. */
    val markedTypes: Set<RealLifeMarkerType> = emptySet()
)

class RealLifeRecordViewModel(
    private val repository: AircraftProfileRepository,
    private val profileId: Long,
    /** Injected so this stays testable on a plain JVM: `SystemClock.elapsedRealtimeNanos()` is a
     * real Android framework call — this project deliberately has no Robolectric (see
     * `FakeDaos.kt`'s KDoc), and calling it unmocked throws at test time. Same pattern
     * [nl.schellenberg.hk36ttc.data.export.UserDataRepository] already uses for
     * `System.currentTimeMillis` via its own `now` parameter. */
    private val elapsedRealtimeNanos: () -> Long = SystemClock::elapsedRealtimeNanos
) : ViewModel() {

    private val _state = MutableStateFlow(RealLifeRecordState())
    val state: StateFlow<RealLifeRecordState> = _state

    private var timeoutJob: Job? = null

    /** Inserts the log header and flips [RealLifeRecordState.isRecording] — the caller
     * ([RealLifeRecordScreen]) is responsible for then calling [RealLifeRecorder.start] with the
     * returned log id. Runs the 10-minute safety timeout (spec §6: "a forgotten recording should
     * not run indefinitely") as a cancellable [viewModelScope] job, so it never fires once
     * [stopRecording] has already been called for any other reason. */
    fun startRecording(barometerAvailable: Boolean, requestedIntervalMs: Long, onStarted: (Long) -> Unit) {
        // Synchronous, before any suspend point -- see RealLifeRecordState.starting's KDoc.
        if (_state.value.isRecording || _state.value.starting) return
        _state.update { it.copy(starting = true, startError = null) }
        val current = _state.value
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val logId = repository.startRealLifeLog(
                RealLifeLogEntity(
                    profileId = profileId,
                    configuration = current.configuration.name,
                    notes = current.notes,
                    startedAtEpochMs = startedAt,
                    stoppedAtEpochMs = null,
                    stopReason = null,
                    barometerAvailable = barometerAvailable,
                    gpsRequestedIntervalMs = requestedIntervalMs
                )
            )
            _state.update {
                it.copy(
                    isRecording = true,
                    starting = false,
                    startedAtEpochMs = startedAt,
                    currentLogId = logId,
                    locationCount = 0,
                    imuCount = 0,
                    barometerCount = 0,
                    lastStopReason = null,
                    markedTypes = emptySet()
                )
            }
            timeoutJob = viewModelScope.launch {
                delay(RECORDING_TIMEOUT_MS)
                stopRecording(RealLifeStopReason.TIMEOUT)
            }
            onStarted(logId)
        }
    }

    /** Called when [RealLifeRecorder.start] never actually registered a listener (e.g. missing
     * location permission) for the log [startRecording] just inserted and flipped [isRecording]
     * true for -- unlike [stopRecording], which only ever ends a recording that was genuinely
     * running, this both resets the stuck-true state and closes out the now-empty log row so it
     * doesn't linger as an open-ended (`stoppedAtEpochMs == null`) entry until the 10-minute
     * safety timeout eventually catches it. */
    fun recordingStartFailed(reason: String) {
        val logId = _state.value.currentLogId
        timeoutJob?.cancel()
        timeoutJob = null
        _state.update { it.copy(isRecording = false, starting = false, startError = reason) }
        if (logId != null) {
            viewModelScope.launch {
                repository.stopRealLifeLog(logId, System.currentTimeMillis(), RealLifeStopReason.START_FAILED.name)
            }
        }
    }

    /** No-ops if nothing is recording — safe to call from multiple triggers (Stop button,
     * backgrounding, timeout) without coordinating which one "wins" first. */
    fun stopRecording(reason: RealLifeStopReason) {
        val logId = _state.value.currentLogId
        if (!_state.value.isRecording || logId == null) return
        timeoutJob?.cancel()
        timeoutJob = null
        _state.update { it.copy(isRecording = false, lastStopReason = reason) }
        viewModelScope.launch {
            repository.stopRealLifeLog(logId, System.currentTimeMillis(), reason.name)
        }
    }

    fun updateConfiguration(configuration: RealLifeConfiguration) {
        if (!_state.value.isRecording) _state.update { it.copy(configuration = configuration) }
    }

    fun updateNotes(notes: String) {
        if (!_state.value.isRecording) _state.update { it.copy(notes = notes) }
    }

    fun onLocationBatch(samples: List<LocationSampleEntity>) {
        if (samples.isEmpty()) return
        viewModelScope.launch { repository.appendLocationSamples(samples) }
        _state.update { it.copy(locationCount = it.locationCount + samples.size) }
    }

    fun onImuBatch(samples: List<ImuSampleEntity>) {
        if (samples.isEmpty()) return
        viewModelScope.launch { repository.appendImuSamples(samples) }
        _state.update { it.copy(imuCount = it.imuCount + samples.size) }
    }

    fun onBarometerBatch(samples: List<BarometerSampleEntity>) {
        if (samples.isEmpty()) return
        viewModelScope.launch { repository.appendBarometerSamples(samples) }
        _state.update { it.copy(barometerCount = it.barometerCount + samples.size) }
    }

    /** Records a co-pilot/observer's ground-truth tap (see [RealLifeMarkerEntity]) at the exact
     * moment it happens — [SystemClock.elapsedRealtimeNanos] is read synchronously, on the UI
     * thread the tap arrives on, not inside the launched coroutine, so the timestamp isn't
     * skewed by however long the DB write takes to actually get scheduled. Same clock domain as
     * every sensor sample, so a marker lines up directly against the recorded trace without any
     * conversion. No-ops outside an active recording, or for a type already marked this
     * recording — the UI only shows the button in the first place while recording, this is
     * belt-and-braces against a double-tap. */
    fun markEvent(type: RealLifeMarkerType) {
        val logId = _state.value.currentLogId
        if (!_state.value.isRecording || logId == null || type in _state.value.markedTypes) return
        val epochMs = System.currentTimeMillis()
        val nowElapsedRealtimeNanos = elapsedRealtimeNanos()
        _state.update { it.copy(markedTypes = it.markedTypes + type) }
        viewModelScope.launch {
            repository.addRealLifeMarker(
                RealLifeMarkerEntity(
                    logId = logId,
                    epochMs = epochMs,
                    elapsedRealtimeNanos = nowElapsedRealtimeNanos,
                    markerType = type.name
                )
            )
        }
    }

    companion object {
        const val RECORDING_TIMEOUT_MS = 10 * 60 * 1000L

        fun factory(repository: AircraftProfileRepository, profileId: Long) =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    @Suppress("UNCHECKED_CAST")
                    return RealLifeRecordViewModel(repository, profileId) as T
                }
            }
    }
}
