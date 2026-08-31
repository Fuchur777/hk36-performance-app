package nl.schellenberg.hk36ttc.data.export

import kotlin.test.Test
import kotlin.test.assertEquals
import nl.schellenberg.hk36ttc.data.local.BarometerSampleEntity
import nl.schellenberg.hk36ttc.data.local.ImuSampleEntity
import nl.schellenberg.hk36ttc.data.local.LocationSampleEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeConfiguration
import nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeMarkerEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeMarkerType
import nl.schellenberg.hk36ttc.data.local.RealLifeStopReason

/** DTO round-trip for the Fase 4a export layer — same spirit as verifying any other DTO layer in
 * this codebase (no equivalent test currently exists for [UserDataExport]'s DTOs either, this is
 * the first). Only field-by-field symmetry is asserted; JSON text formatting is not. */
class RealLifeLogExportTest {

    @Test
    fun `RealLifeLogEntity survives a toDto round-trip, including the Fase 4c conditions fields`() {
        val entity = RealLifeLogEntity(
            id = 7, profileId = 3, configuration = RealLifeConfiguration.SLEEPVLUCHT.name,
            notes = "gras, telefoon los", startedAtEpochMs = 1_000L, stoppedAtEpochMs = 5_000L,
            stopReason = RealLifeStopReason.MANUAL.name, barometerAvailable = true, gpsRequestedIntervalMs = 1_000L,
            airfieldId = 4L, surfaceType = "ASFALT", slopePct = 1.5, oatC = 18, pressureAltM = 30,
            windDirectionDeg = 250, windSpeedKts = 8, metarRaw = "EHGR 010000Z 25008KT 9999 18/12 Q1015",
            metarObservedAtEpochMs = 900L, conditionsSource = "METAR_HISTORICAL"
        )
        val dto = entity.toDto()

        assertEquals(entity.id, dto.id)
        assertEquals(entity.configuration, dto.configuration)
        assertEquals(entity.notes, dto.notes)
        assertEquals(entity.startedAtEpochMs, dto.startedAtEpochMs)
        assertEquals(entity.stoppedAtEpochMs, dto.stoppedAtEpochMs)
        assertEquals(entity.stopReason, dto.stopReason)
        assertEquals(entity.barometerAvailable, dto.barometerAvailable)
        assertEquals(entity.gpsRequestedIntervalMs, dto.gpsRequestedIntervalMs)
        assertEquals(entity.airfieldId, dto.airfieldId)
        assertEquals(entity.surfaceType, dto.surfaceType)
        assertEquals(entity.slopePct, dto.slopePct)
        assertEquals(entity.oatC, dto.oatC)
        assertEquals(entity.pressureAltM, dto.pressureAltM)
        assertEquals(entity.windDirectionDeg, dto.windDirectionDeg)
        assertEquals(entity.windSpeedKts, dto.windSpeedKts)
        assertEquals(entity.metarRaw, dto.metarRaw)
        assertEquals(entity.metarObservedAtEpochMs, dto.metarObservedAtEpochMs)
        assertEquals(entity.conditionsSource, dto.conditionsSource)
    }

    @Test
    fun `LocationSampleEntity survives a toDto round-trip, nulls included`() {
        val entity = LocationSampleEntity(
            id = 1, logId = 7, epochMs = 1_100L, elapsedRealtimeNanos = 100L,
            latitude = 52.123, longitude = 5.456, altitudeM = null, speedMps = 12.3f,
            speedAccuracyMps = null, bearingDeg = 90f, bearingAccuracyDeg = null,
            horizontalAccuracyM = 3f, verticalAccuracyM = null
        )
        val dto = entity.toDto()

        assertEquals(entity.epochMs, dto.epochMs)
        assertEquals(entity.elapsedRealtimeNanos, dto.elapsedRealtimeNanos)
        assertEquals(entity.latitude, dto.latitude)
        assertEquals(entity.longitude, dto.longitude)
        assertEquals(entity.altitudeM, dto.altitudeM)
        assertEquals(entity.speedMps, dto.speedMps)
        assertEquals(entity.speedAccuracyMps, dto.speedAccuracyMps)
        assertEquals(entity.bearingDeg, dto.bearingDeg)
        assertEquals(entity.bearingAccuracyDeg, dto.bearingAccuracyDeg)
        assertEquals(entity.horizontalAccuracyM, dto.horizontalAccuracyM)
        assertEquals(entity.verticalAccuracyM, dto.verticalAccuracyM)
    }

    @Test
    fun `RealLifeMarkerEntity survives a toDto round-trip`() {
        val entity = RealLifeMarkerEntity(
            id = 1, logId = 7, epochMs = 1_500L, elapsedRealtimeNanos = 500L,
            markerType = RealLifeMarkerType.LIFT_OFF.name
        )
        val dto = entity.toDto()

        assertEquals(entity.epochMs, dto.epochMs)
        assertEquals(entity.elapsedRealtimeNanos, dto.elapsedRealtimeNanos)
        assertEquals(entity.markerType, dto.markerType)
    }

    @Test
    fun `a full export serializes and deserializes back to an equal structure`() {
        val export = RealLifeLogExport(
            exportedAtEpochMs = 42L,
            appVersionName = "0.9.5",
            registration = "PH-1600",
            log = RealLifeLogEntity(
                id = 1, profileId = 1, configuration = RealLifeConfiguration.NORMAL.name, notes = "",
                startedAtEpochMs = 1L, stoppedAtEpochMs = 2L, stopReason = RealLifeStopReason.MANUAL.name,
                barometerAvailable = false, gpsRequestedIntervalMs = 1_000L
            ).toDto(),
            locationSamples = listOf(
                LocationSampleEntity(
                    id = 1, logId = 1, epochMs = 1L, elapsedRealtimeNanos = 1L, latitude = 1.0, longitude = 2.0,
                    altitudeM = 3.0, speedMps = 4f, speedAccuracyMps = 5f, bearingDeg = 6f,
                    bearingAccuracyDeg = 7f, horizontalAccuracyM = 8f, verticalAccuracyM = 9f
                ).toDto()
            ),
            imuSamples = listOf(
                ImuSampleEntity(id = 1, logId = 1, epochMs = 1L, elapsedRealtimeNanos = 1L, sensorType = "ACCELEROMETER", x = 1f, y = 2f, z = 3f, accuracy = 3).toDto()
            ),
            barometerSamples = listOf(
                BarometerSampleEntity(id = 1, logId = 1, epochMs = 1L, elapsedRealtimeNanos = 1L, pressureHpa = 1013.2f).toDto()
            ),
            markers = listOf(
                RealLifeMarkerEntity(id = 1, logId = 1, epochMs = 1L, elapsedRealtimeNanos = 1L, markerType = RealLifeMarkerType.ROLL_START.name).toDto()
            )
        )

        val json = realLifeLogJson.encodeToString(export)
        val decoded = realLifeLogJson.decodeFromString<RealLifeLogExport>(json)

        assertEquals(export, decoded)
    }
}
