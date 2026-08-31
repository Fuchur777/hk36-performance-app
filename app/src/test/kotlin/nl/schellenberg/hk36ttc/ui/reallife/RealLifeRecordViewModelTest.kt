package nl.schellenberg.hk36ttc.ui.reallife

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.schellenberg.hk36ttc.data.local.LocationSampleEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeConfiguration
import nl.schellenberg.hk36ttc.data.local.RealLifeMarkerType
import nl.schellenberg.hk36ttc.data.local.RealLifeStopReason
import nl.schellenberg.hk36ttc.data.local.fakeAircraftProfileRepository

/** Same `Dispatchers.setMain`/`testScheduler` pattern as `TakeoffViewModelTest.kt` — the standard
 * way to give `viewModelScope` a controllable virtual clock in a plain JVM test. */
@OptIn(ExperimentalCoroutinesApi::class)
class RealLifeRecordViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startRecording inserts a header row and flips isRecording`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1)
        var startedLogId: Long? = null

        viewModel.startRecording(barometerAvailable = true, requestedIntervalMs = 1_000L) { startedLogId = it }
        testScheduler.runCurrent()

        assertNotNull(startedLogId)
        assertEquals(true, viewModel.state.value.isRecording)
        assertEquals(startedLogId, viewModel.state.value.currentLogId)
        assertNotNull(repository.getRealLifeLog(startedLogId))
    }

    @Test
    fun `sample batches accumulate counts and persist via the repository`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1)
        var logId = 0L
        viewModel.startRecording(barometerAvailable = false, requestedIntervalMs = 1_000L) { logId = it }
        testScheduler.runCurrent()

        viewModel.onLocationBatch(
            listOf(
                LocationSampleEntity(
                    logId = logId, epochMs = 1L, elapsedRealtimeNanos = 1L, latitude = 0.0, longitude = 0.0,
                    altitudeM = null, speedMps = null, speedAccuracyMps = null, bearingDeg = null,
                    bearingAccuracyDeg = null, horizontalAccuracyM = null, verticalAccuracyM = null
                )
            )
        )
        testScheduler.runCurrent()

        assertEquals(1, viewModel.state.value.locationCount)
        assertEquals(1, repository.countLocationSamples(logId))
    }

    @Test
    fun `stopRecording MANUAL cancels the safety timeout, so it never fires afterward`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1)
        viewModel.startRecording(barometerAvailable = false, requestedIntervalMs = 1_000L) {}
        testScheduler.runCurrent()

        viewModel.stopRecording(RealLifeStopReason.MANUAL)
        testScheduler.runCurrent()
        assertEquals(RealLifeStopReason.MANUAL, viewModel.state.value.lastStopReason)

        // Advance well past the timeout window; if the job weren't cancelled this would flip
        // lastStopReason to TIMEOUT.
        testScheduler.advanceTimeBy(RealLifeRecordViewModel.RECORDING_TIMEOUT_MS + 1_000L)
        testScheduler.runCurrent()
        assertEquals(RealLifeStopReason.MANUAL, viewModel.state.value.lastStopReason)
    }

    @Test
    fun `the safety timeout fires stopRecording TIMEOUT when nothing else stops it first`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1)
        viewModel.startRecording(barometerAvailable = false, requestedIntervalMs = 1_000L) {}
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(RealLifeRecordViewModel.RECORDING_TIMEOUT_MS + 1_000L)
        testScheduler.runCurrent()

        assertEquals(false, viewModel.state.value.isRecording)
        assertEquals(RealLifeStopReason.TIMEOUT, viewModel.state.value.lastStopReason)
    }

    @Test
    fun `updateConfiguration and updateNotes are ignored while a recording is in progress`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1)
        viewModel.updateConfiguration(RealLifeConfiguration.SLEEPVLUCHT)
        testScheduler.runCurrent()
        assertEquals(RealLifeConfiguration.SLEEPVLUCHT, viewModel.state.value.configuration)

        viewModel.startRecording(barometerAvailable = false, requestedIntervalMs = 1_000L) {}
        testScheduler.runCurrent()

        viewModel.updateConfiguration(RealLifeConfiguration.NORMAL)
        assertEquals(RealLifeConfiguration.SLEEPVLUCHT, viewModel.state.value.configuration)
    }

    @Test
    fun `markEvent records a ground-truth marker and flips it to done, but only once`() = runTest {
        val repository = fakeAircraftProfileRepository()
        // Real SystemClock.elapsedRealtimeNanos() throws on a plain JVM test (no Robolectric in
        // this project) — inject a fake, same shape as the codebase's other injected-clock params.
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1, elapsedRealtimeNanos = { 42L })
        var logId = 0L
        viewModel.startRecording(barometerAvailable = false, requestedIntervalMs = 1_000L) { logId = it }
        testScheduler.runCurrent()

        viewModel.markEvent(RealLifeMarkerType.LIFT_OFF)
        testScheduler.runCurrent()

        assertEquals(setOf(RealLifeMarkerType.LIFT_OFF), viewModel.state.value.markedTypes)
        val markers = repository.getRealLifeMarkers(logId)
        assertEquals(1, markers.size)
        assertEquals(RealLifeMarkerType.LIFT_OFF.name, markers.single().markerType)

        // A second tap for the same type must not add a duplicate row.
        viewModel.markEvent(RealLifeMarkerType.LIFT_OFF)
        testScheduler.runCurrent()
        assertEquals(1, repository.getRealLifeMarkers(logId).size)
    }

    @Test
    fun `markEvent is a no-op when nothing is recording`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1)

        viewModel.markEvent(RealLifeMarkerType.ROLL_START)
        testScheduler.runCurrent()

        assertEquals(emptySet(), viewModel.state.value.markedTypes)
    }

    @Test
    fun `stopRecording is a no-op when nothing is recording`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1)

        viewModel.stopRecording(RealLifeStopReason.MANUAL)
        testScheduler.runCurrent()

        assertNull(viewModel.state.value.lastStopReason)
    }

    @Test
    fun `a second startRecording call while the first is still in flight is a no-op`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1)
        var startedCount = 0

        // Only runCurrent(), not advanceUntilIdle() -- the point is to call startRecording again
        // BEFORE the first call's coroutine (the DB insert + state update) has had a chance to
        // run, the exact window a rapid double-tap on the Start button would land in.
        viewModel.startRecording(barometerAvailable = false, requestedIntervalMs = 1_000L) { startedCount++ }
        viewModel.startRecording(barometerAvailable = false, requestedIntervalMs = 1_000L) { startedCount++ }
        testScheduler.advanceUntilIdle()

        assertEquals(1, startedCount)
        assertEquals(1, repository.observeRealLifeLogs(1).first().size)
    }

    @Test
    fun `recordingStartFailed resets isRecording and closes the log row with START_FAILED`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val viewModel = RealLifeRecordViewModel(repository, profileId = 1)
        var logId = 0L
        viewModel.startRecording(barometerAvailable = false, requestedIntervalMs = 1_000L) { logId = it }
        testScheduler.runCurrent()
        assertEquals(true, viewModel.state.value.isRecording)

        viewModel.recordingStartFailed("Locatietoestemming ontbreekt")
        testScheduler.runCurrent()

        assertEquals(false, viewModel.state.value.isRecording)
        assertEquals("Locatietoestemming ontbreekt", viewModel.state.value.startError)
        assertEquals(RealLifeStopReason.START_FAILED.name, repository.getRealLifeLog(logId)?.stopReason)

        // The safety timeout must also be cancelled -- otherwise it would still fire and
        // overwrite lastStopReason/startError well after the pilot has already been told why.
        testScheduler.advanceTimeBy(RealLifeRecordViewModel.RECORDING_TIMEOUT_MS + 1_000L)
        testScheduler.runCurrent()
        assertNull(viewModel.state.value.lastStopReason)
    }
}
