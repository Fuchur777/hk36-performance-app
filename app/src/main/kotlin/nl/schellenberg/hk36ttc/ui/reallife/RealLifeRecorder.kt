package nl.schellenberg.hk36ttc.ui.reallife

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.schellenberg.hk36ttc.data.local.BarometerSampleEntity
import nl.schellenberg.hk36ttc.data.local.ImuSampleEntity
import nl.schellenberg.hk36ttc.data.local.LocationSampleEntity

/**
 * Owns every Android-platform sensor/location API a Fase 4a recording needs (FusedLocationProviderClient
 * + [SensorManager]) and nothing else — no persistence, no recording-session policy (start/stop
 * reasons, timeouts). That lives in [RealLifeRecordViewModel], which never holds a [Context];
 * this class is the one place in the feature that does, and is meant to be constructed/owned by
 * the Composable via `remember` (see `RealLifeRecordScreen.kt`), not by the ViewModel.
 *
 * Buffers samples internally and flushes them via the batch callbacks periodically (or once a
 * buffer gets large) — accelerometer/gyroscope can fire at up to 200 Hz, so a per-event callback
 * straight to Room would be excessive write churn (see [LocationSampleEntity]'s KDoc).
 */
class RealLifeRecorder(
    private val context: Context,
    private val requestedIntervalMs: Long,
    private val onLocationBatch: (List<LocationSampleEntity>) -> Unit,
    private val onImuBatch: (List<ImuSampleEntity>) -> Unit,
    private val onBarometerBatch: (List<BarometerSampleEntity>) -> Unit,
    private val onError: (message: String) -> Unit
) {
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private var logId: Long = 0
    private var flushScope: CoroutineScope? = null

    private val bufferLock = Any()
    private val locationBuffer = mutableListOf<LocationSampleEntity>()
    private val imuBuffer = mutableListOf<ImuSampleEntity>()
    private val barometerBuffer = mutableListOf<BarometerSampleEntity>()

    private var locationCallback: LocationCallback? = null
    private var accelerometerListener: SensorEventListener? = null
    private var gyroscopeListener: SensorEventListener? = null
    private var barometerListener: SensorEventListener? = null

    fun barometerAvailable(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    /** Starts recording into [logId] (the already-inserted [nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity]'s
     * id). No-ops with [onError] if location permission isn't actually granted — the UI-level
     * [LocationPermissionGate] should already prevent this, this is belt-and-braces. */
    @SuppressLint("MissingPermission")
    fun start(logId: Long) {
        this.logId = logId
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError("Locatietoestemming ontbreekt")
            return
        }

        val locationRequest = LocationRequest.Builder(requestedIntervalMs)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(requestedIntervalMs)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::addLocationSample)
            }
        }
        locationCallback = callback
        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())

        registerImuListener(Sensor.TYPE_ACCELEROMETER, "ACCELEROMETER") { accelerometerListener = it }
        registerImuListener(Sensor.TYPE_GYROSCOPE, "GYROSCOPE") { gyroscopeListener = it }
        registerBarometerListener()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        flushScope = scope
        scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    /** Removes every listener, cancels the flush loop, and flushes whatever is still buffered —
     * must always run, even on an error path, or GPS/sensors keep running after the screen is
     * gone (the exact battery-drain risk the spec calls out). */
    fun stop() {
        flushScope?.cancel()
        flushScope = null

        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        accelerometerListener?.let { sensorManager.unregisterListener(it) }
        accelerometerListener = null
        gyroscopeListener?.let { sensorManager.unregisterListener(it) }
        gyroscopeListener = null
        barometerListener?.let { sensorManager.unregisterListener(it) }
        barometerListener = null

        flush()
    }

    private fun registerImuListener(sensorType: Int, label: String, remember: (SensorEventListener) -> Unit) {
        val sensor = sensorManager.getDefaultSensor(sensorType) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = addImuSample(label, event)
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        remember(listener)
        sensorManager.registerListener(listener, sensor, SAMPLING_PERIOD_US, MAX_REPORT_LATENCY_US)
    }

    private fun registerBarometerListener() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                synchronized(bufferLock) {
                    barometerBuffer += BarometerSampleEntity(
                        logId = logId,
                        epochMs = System.currentTimeMillis(),
                        elapsedRealtimeNanos = event.timestamp,
                        pressureHpa = event.values[0]
                    )
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        barometerListener = listener
        sensorManager.registerListener(listener, sensor, SAMPLING_PERIOD_US, MAX_REPORT_LATENCY_US)
    }

    private fun addLocationSample(location: Location) {
        synchronized(bufferLock) {
            locationBuffer += LocationSampleEntity(
                logId = logId,
                epochMs = location.time,
                elapsedRealtimeNanos = location.elapsedRealtimeNanos,
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeM = if (location.hasAltitude()) location.altitude else null,
                speedMps = if (location.hasSpeed()) location.speed else null,
                speedAccuracyMps = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null,
                bearingDeg = if (location.hasBearing()) location.bearing else null,
                bearingAccuracyDeg = if (location.hasBearingAccuracy()) location.bearingAccuracyDegrees else null,
                horizontalAccuracyM = if (location.hasAccuracy()) location.accuracy else null,
                verticalAccuracyM = if (location.hasVerticalAccuracy()) location.verticalAccuracyMeters else null
            )
        }
    }

    private fun addImuSample(sensorType: String, event: SensorEvent) {
        synchronized(bufferLock) {
            imuBuffer += ImuSampleEntity(
                logId = logId,
                epochMs = System.currentTimeMillis(),
                elapsedRealtimeNanos = event.timestamp,
                sensorType = sensorType,
                x = event.values[0],
                y = event.values[1],
                z = event.values[2],
                accuracy = event.accuracy
            )
        }
    }

    private fun flush() {
        val (locations, imu, barometer) = synchronized(bufferLock) {
            val snapshot = Triple(locationBuffer.toList(), imuBuffer.toList(), barometerBuffer.toList())
            locationBuffer.clear()
            imuBuffer.clear()
            barometerBuffer.clear()
            snapshot
        }
        if (locations.isNotEmpty()) onLocationBatch(locations)
        if (imu.isNotEmpty()) onImuBatch(imu)
        if (barometer.isNotEmpty()) onBarometerBatch(barometer)
    }

    private companion object {
        /** ~50 Hz, not `SENSOR_DELAY_FASTEST` (up to 200 Hz) — still ~50x GPS's rate, plenty for
         * a later phase's event-timing need, while keeping worst-case row counts sane at the
         * 10-minute recording timeout. A tunable, not an architectural constraint. */
        const val SAMPLING_PERIOD_US = 20_000
        /** 0 disables hardware FIFO batching, which would otherwise fight the buffered-flush
         * design above by delivering samples in delayed bursts instead of as they occur. */
        const val MAX_REPORT_LATENCY_US = 0
        const val FLUSH_INTERVAL_MS = 1_000L
    }
}
