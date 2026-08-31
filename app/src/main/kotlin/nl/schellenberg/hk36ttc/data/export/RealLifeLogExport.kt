package nl.schellenberg.hk36ttc.data.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.schellenberg.hk36ttc.data.local.BarometerSampleEntity
import nl.schellenberg.hk36ttc.data.local.ImuSampleEntity
import nl.schellenberg.hk36ttc.data.local.LocationSampleEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity
import nl.schellenberg.hk36ttc.data.local.RealLifeMarkerEntity

/**
 * Export for a single Fase 4a "Real Life Performance" recording — deliberately separate from
 * [UserDataExport], whose own doc comment already excludes bulk/rebuildable data and which builds
 * its JSON as one pretty-printed in-memory object. A single recording can run to tens of
 * thousands of sample rows (worst case: the 10-minute recording timeout at ~50 Hz on two IMU
 * sensors is ~60,000 rows), where [UserDataExport]'s approach would be unsuited on both counts.
 */
@Serializable
data class RealLifeLogExport(
    @SerialName("schema_version") val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("exported_at_epoch_ms") val exportedAtEpochMs: Long,
    @SerialName("app_version_name") val appVersionName: String,
    @SerialName("registration") val registration: String?,
    @SerialName("log") val log: RealLifeLogDto,
    @SerialName("location_samples") val locationSamples: List<LocationSampleDto>,
    @SerialName("imu_samples") val imuSamples: List<ImuSampleDto>,
    @SerialName("barometer_samples") val barometerSamples: List<BarometerSampleDto>,
    /** Ground-truth taps from a co-pilot/observer, see [RealLifeMarkerEntity]. Defaulted so an
     * export from before this field existed still decodes, though nothing in this app currently
     * re-imports one of these files — export is one-way, out to the pilot's own analysis. */
    @SerialName("markers") val markers: List<MarkerDto> = emptyList()
) {
    companion object {
        /** Bumped to 2 when the Fase 4c conditions/weather fields were added to [RealLifeLogDto]
         * -- [ignoreUnknownKeys] on [realLifeLogJson] means an older-schema file still decodes
         * fine (the new fields just default to null), so this is a courtesy marker, not a hard
         * compatibility gate the way [nl.schellenberg.hk36ttc.data.export.UserDataExport]'s
         * version is. */
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

@Serializable
data class RealLifeLogDto(
    val id: Long,
    val configuration: String,
    val notes: String,
    @SerialName("started_at_epoch_ms") val startedAtEpochMs: Long,
    @SerialName("stopped_at_epoch_ms") val stoppedAtEpochMs: Long?,
    @SerialName("stop_reason") val stopReason: String?,
    @SerialName("barometer_available") val barometerAvailable: Boolean,
    @SerialName("gps_requested_interval_ms") val gpsRequestedIntervalMs: Long,
    /** Fase 4c conditions snapshot -- see [nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity]'s
     * own KDoc for why every one of these is nullable. Defaulted to null so a file decoded by an
     * older build (schema 1) still reads, per [ignoreUnknownKeys]. */
    @SerialName("airfield_id") val airfieldId: Long? = null,
    @SerialName("surface_type") val surfaceType: String? = null,
    @SerialName("slope_pct") val slopePct: Double? = null,
    @SerialName("oat_c") val oatC: Int? = null,
    @SerialName("pressure_alt_m") val pressureAltM: Int? = null,
    @SerialName("wind_direction_deg") val windDirectionDeg: Int? = null,
    @SerialName("wind_speed_kts") val windSpeedKts: Int? = null,
    @SerialName("metar_raw") val metarRaw: String? = null,
    @SerialName("metar_observed_at_epoch_ms") val metarObservedAtEpochMs: Long? = null,
    @SerialName("conditions_source") val conditionsSource: String? = null
)

@Serializable
data class LocationSampleDto(
    @SerialName("epoch_ms") val epochMs: Long,
    @SerialName("elapsed_realtime_nanos") val elapsedRealtimeNanos: Long,
    val latitude: Double,
    val longitude: Double,
    @SerialName("altitude_m") val altitudeM: Double?,
    @SerialName("speed_mps") val speedMps: Float?,
    @SerialName("speed_accuracy_mps") val speedAccuracyMps: Float?,
    @SerialName("bearing_deg") val bearingDeg: Float?,
    @SerialName("bearing_accuracy_deg") val bearingAccuracyDeg: Float?,
    @SerialName("horizontal_accuracy_m") val horizontalAccuracyM: Float?,
    @SerialName("vertical_accuracy_m") val verticalAccuracyM: Float?
)

@Serializable
data class ImuSampleDto(
    @SerialName("epoch_ms") val epochMs: Long,
    @SerialName("elapsed_realtime_nanos") val elapsedRealtimeNanos: Long,
    @SerialName("sensor_type") val sensorType: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int
)

@Serializable
data class BarometerSampleDto(
    @SerialName("epoch_ms") val epochMs: Long,
    @SerialName("elapsed_realtime_nanos") val elapsedRealtimeNanos: Long,
    @SerialName("pressure_hpa") val pressureHpa: Float
)

@Serializable
data class MarkerDto(
    @SerialName("epoch_ms") val epochMs: Long,
    @SerialName("elapsed_realtime_nanos") val elapsedRealtimeNanos: Long,
    @SerialName("marker_type") val markerType: String
)

fun RealLifeLogEntity.toDto() = RealLifeLogDto(
    id = id,
    configuration = configuration,
    notes = notes,
    startedAtEpochMs = startedAtEpochMs,
    stoppedAtEpochMs = stoppedAtEpochMs,
    stopReason = stopReason,
    barometerAvailable = barometerAvailable,
    gpsRequestedIntervalMs = gpsRequestedIntervalMs,
    airfieldId = airfieldId,
    surfaceType = surfaceType,
    slopePct = slopePct,
    oatC = oatC,
    pressureAltM = pressureAltM,
    windDirectionDeg = windDirectionDeg,
    windSpeedKts = windSpeedKts,
    metarRaw = metarRaw,
    metarObservedAtEpochMs = metarObservedAtEpochMs,
    conditionsSource = conditionsSource
)

fun LocationSampleEntity.toDto() = LocationSampleDto(
    epochMs = epochMs,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    latitude = latitude,
    longitude = longitude,
    altitudeM = altitudeM,
    speedMps = speedMps,
    speedAccuracyMps = speedAccuracyMps,
    bearingDeg = bearingDeg,
    bearingAccuracyDeg = bearingAccuracyDeg,
    horizontalAccuracyM = horizontalAccuracyM,
    verticalAccuracyM = verticalAccuracyM
)

fun ImuSampleEntity.toDto() = ImuSampleDto(
    epochMs = epochMs,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    sensorType = sensorType,
    x = x, y = y, z = z,
    accuracy = accuracy
)

fun BarometerSampleEntity.toDto() = BarometerSampleDto(
    epochMs = epochMs,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    pressureHpa = pressureHpa
)

fun RealLifeMarkerEntity.toDto() = MarkerDto(
    epochMs = epochMs,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    markerType = markerType
)

/** Not pretty-printed, unlike [userDataJson] — see this file's own doc comment for why: a single
 * recording's row count makes indentation pure overhead. */
val realLifeLogJson = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
}
