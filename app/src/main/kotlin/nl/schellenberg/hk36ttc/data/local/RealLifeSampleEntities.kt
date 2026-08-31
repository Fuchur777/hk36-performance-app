package nl.schellenberg.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Raw sample tables for a [RealLifeLogEntity] recording. Every row carries both [epochMs]
 * (wall-clock, for the exported JSON and human display) and [elapsedRealtimeNanos] (monotonic,
 * same clock domain as `Location.getElapsedRealtimeNanos()`/`SensorEvent.timestamp`) — the
 * monotonic value is what a later detection phase needs to align a GPS fix with an IMU spike
 * precisely, and can't be reconstructed after the fact from wall-clock timestamps alone (which
 * can jump on NTP correction).
 *
 * `@Index("logId")` on every table here, unlike most owner-id columns elsewhere in this schema
 * (e.g. [RunwayStripEntity.airfieldId]): those tables hold at most dozens of rows per owner, but
 * a single recording can produce tens of thousands of IMU rows, where an unindexed
 * `WHERE logId = ...` would be a full table scan. [RunwayCatalogEntity]'s `airportIdent` index is
 * the existing precedent for indexing an owner column once row volume is large.
 *
 * DAOs are batch-insert only (`insertAll`) — accelerometer/gyroscope can fire at up to 200 Hz, so
 * a per-event `@Insert` would be excessive write churn. The recorder (`ui/reallife/RealLifeRecorder.kt`)
 * is responsible for buffering samples and flushing them in batches.
 */
@Entity(tableName = "location_samples", indices = [Index("logId")])
data class LocationSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logId: Long,
    val epochMs: Long,
    val elapsedRealtimeNanos: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val speedMps: Float?,
    /** Null when `Location.hasSpeedAccuracy()` was false — never read the raw getter unguarded,
     * it silently returns 0.0 rather than throwing, which would be indistinguishable from a
     * genuine zero-accuracy reading. */
    val speedAccuracyMps: Float?,
    val bearingDeg: Float?,
    val bearingAccuracyDeg: Float?,
    val horizontalAccuracyM: Float?,
    val verticalAccuracyM: Float?
)

/** One combined table for both IMU sensors, distinguished by [sensorType], rather than separate
 * accelerometer/gyroscope tables — the two sensors deliver on independent, unsynchronized
 * callback streams, so zipping them into one row per timestamp would fabricate a synchronization
 * that doesn't exist in the raw data. */
@Entity(tableName = "imu_samples", indices = [Index("logId")])
data class ImuSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logId: Long,
    val epochMs: Long,
    val elapsedRealtimeNanos: Long,
    /** "ACCELEROMETER" or "GYROSCOPE". */
    val sensorType: String,
    val x: Float,
    val y: Float,
    val z: Float,
    /** Raw `SensorEvent.accuracy`. */
    val accuracy: Int
)

@Entity(tableName = "barometer_samples", indices = [Index("logId")])
data class BarometerSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logId: Long,
    val epochMs: Long,
    val elapsedRealtimeNanos: Long,
    val pressureHpa: Float
)

@Dao
interface LocationSampleDao {
    @Insert
    suspend fun insertAll(samples: List<LocationSampleEntity>)

    @Query("SELECT * FROM location_samples WHERE logId = :logId ORDER BY epochMs ASC")
    suspend fun getByLog(logId: Long): List<LocationSampleEntity>

    /** For a log-detail summary — avoids loading potentially tens of thousands of rows into
     * memory just to show a count. */
    @Query("SELECT COUNT(*) FROM location_samples WHERE logId = :logId")
    suspend fun countByLog(logId: Long): Int

    @Query("DELETE FROM location_samples WHERE logId = :logId")
    suspend fun deleteByLogId(logId: Long)
}

@Dao
interface ImuSampleDao {
    @Insert
    suspend fun insertAll(samples: List<ImuSampleEntity>)

    @Query("SELECT * FROM imu_samples WHERE logId = :logId ORDER BY epochMs ASC")
    suspend fun getByLog(logId: Long): List<ImuSampleEntity>

    @Query("SELECT COUNT(*) FROM imu_samples WHERE logId = :logId")
    suspend fun countByLog(logId: Long): Int

    @Query("DELETE FROM imu_samples WHERE logId = :logId")
    suspend fun deleteByLogId(logId: Long)
}

@Dao
interface BarometerSampleDao {
    @Insert
    suspend fun insertAll(samples: List<BarometerSampleEntity>)

    @Query("SELECT * FROM barometer_samples WHERE logId = :logId ORDER BY epochMs ASC")
    suspend fun getByLog(logId: Long): List<BarometerSampleEntity>

    @Query("SELECT COUNT(*) FROM barometer_samples WHERE logId = :logId")
    suspend fun countByLog(logId: Long): Int

    @Query("DELETE FROM barometer_samples WHERE logId = :logId")
    suspend fun deleteByLogId(logId: Long)
}
