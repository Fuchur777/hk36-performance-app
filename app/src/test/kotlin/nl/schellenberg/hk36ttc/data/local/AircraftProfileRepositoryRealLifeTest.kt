package nl.schellenberg.hk36ttc.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * Fase 4a repository round-trip, using the same [fakeAircraftProfileRepository] pattern every
 * other JVM ViewModel/repository test in this module uses. Cascade-delete coverage matters most
 * here — a forgotten sample table left un-cleared is exactly the class of bug
 * [AircraftProfileRepository.deleteProfileCascade]'s own KDoc already warns about.
 */
class AircraftProfileRepositoryRealLifeTest {

    private fun log(profileId: Long = 1, id: Long = 0) = RealLifeLogEntity(
        id = id,
        profileId = profileId,
        configuration = RealLifeConfiguration.NORMAL.name,
        notes = "asfalt, telefoon gemonteerd",
        startedAtEpochMs = 1_000L,
        stoppedAtEpochMs = null,
        stopReason = null,
        barometerAvailable = true,
        gpsRequestedIntervalMs = 1_000L
    )

    @Test
    fun `start, append and stop round-trip through the repository`() = runTest {
        val repository = fakeAircraftProfileRepository()

        val logId = repository.startRealLifeLog(log())
        repository.appendLocationSamples(
            listOf(
                LocationSampleEntity(
                    logId = logId, epochMs = 1_100L, elapsedRealtimeNanos = 100L,
                    latitude = 52.0, longitude = 5.0, altitudeM = 10.0, speedMps = 5f,
                    speedAccuracyMps = 0.5f, bearingDeg = 90f, bearingAccuracyDeg = 2f,
                    horizontalAccuracyM = 3f, verticalAccuracyM = 4f
                )
            )
        )
        repository.appendImuSamples(
            listOf(ImuSampleEntity(logId = logId, epochMs = 1_100L, elapsedRealtimeNanos = 100L, sensorType = "ACCELEROMETER", x = 0f, y = 0f, z = 9.8f, accuracy = 3))
        )
        repository.appendBarometerSamples(
            listOf(BarometerSampleEntity(logId = logId, epochMs = 1_100L, elapsedRealtimeNanos = 100L, pressureHpa = 1013f))
        )
        repository.stopRealLifeLog(logId, 2_000L, RealLifeStopReason.MANUAL.name)

        val stored = repository.getRealLifeLog(logId)
        assertEquals(2_000L, stored?.stoppedAtEpochMs)
        assertEquals(RealLifeStopReason.MANUAL.name, stored?.stopReason)
        assertEquals(1, repository.countLocationSamples(logId))
        assertEquals(1, repository.countImuSamples(logId))
        assertEquals(1, repository.getBarometerSamples(logId).size)
    }

    @Test
    fun `deleteRealLifeLogCascade clears every sample table`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val logId = repository.startRealLifeLog(log())
        repository.appendLocationSamples(listOf(
            LocationSampleEntity(logId = logId, epochMs = 1L, elapsedRealtimeNanos = 1L, latitude = 0.0, longitude = 0.0, altitudeM = null, speedMps = null, speedAccuracyMps = null, bearingDeg = null, bearingAccuracyDeg = null, horizontalAccuracyM = null, verticalAccuracyM = null)
        ))
        repository.appendImuSamples(listOf(
            ImuSampleEntity(logId = logId, epochMs = 1L, elapsedRealtimeNanos = 1L, sensorType = "GYROSCOPE", x = 0f, y = 0f, z = 0f, accuracy = 3)
        ))
        repository.appendBarometerSamples(listOf(
            BarometerSampleEntity(logId = logId, epochMs = 1L, elapsedRealtimeNanos = 1L, pressureHpa = 1013f)
        ))
        repository.addRealLifeMarker(
            RealLifeMarkerEntity(logId = logId, epochMs = 1L, elapsedRealtimeNanos = 1L, markerType = RealLifeMarkerType.ROLL_START.name)
        )

        val stored = repository.getRealLifeLog(logId)!!
        repository.deleteRealLifeLogCascade(stored)

        assertEquals(null, repository.getRealLifeLog(logId))
        assertEquals(0, repository.countLocationSamples(logId))
        assertEquals(0, repository.countImuSamples(logId))
        assertTrue(repository.getBarometerSamples(logId).isEmpty())
        assertTrue(repository.getRealLifeMarkers(logId).isEmpty())
    }

    @Test
    fun `deleteProfileCascade also clears every real-life log and its samples for that profile`() = runTest {
        val repository = fakeAircraftProfileRepository()
        val profile = AircraftProfileEntity(
            id = 1, registration = "PH-1600", emptyMassKg = 560.0, emptyMassCgPositionMm = 400.0,
            mtowKg = 770.0, cgEnvelopeForwardLimitMm = 360.0, cgEnvelopeAftLimitMm = 420.0,
            fuelTankType = nl.schellenberg.hk36ttc.core.wb.FuelTankType.STANDARD_55L
        )
        val logId = repository.startRealLifeLog(log(profileId = profile.id))
        repository.appendLocationSamples(listOf(
            LocationSampleEntity(logId = logId, epochMs = 1L, elapsedRealtimeNanos = 1L, latitude = 0.0, longitude = 0.0, altitudeM = null, speedMps = null, speedAccuracyMps = null, bearingDeg = null, bearingAccuracyDeg = null, horizontalAccuracyM = null, verticalAccuracyM = null)
        ))
        repository.addRealLifeMarker(
            RealLifeMarkerEntity(logId = logId, epochMs = 1L, elapsedRealtimeNanos = 1L, markerType = RealLifeMarkerType.LIFT_OFF.name)
        )

        repository.deleteProfileCascade(profile)

        assertEquals(null, repository.getRealLifeLog(logId))
        assertEquals(0, repository.countLocationSamples(logId))
        assertTrue(repository.getRealLifeMarkers(logId).isEmpty())
        assertTrue(repository.observeRealLifeLogs(profile.id).first().isEmpty())
    }
}
