package nl.schellenberg.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** A ground-truth event a co-pilot/observer marks by tapping a button *during* an active
 * recording — see `RealLifeRecordScreen.kt`'s marker row. Existing detection needs some way to
 * check itself against a genuinely independent reference, not just "this looks plausible" —
 * these three points are exactly [nl.schellenberg.hk36ttc.data.local.RealLifeLogEntity]'s
 * `startedAtEpochMs`/roll/lift-off/15m boundaries, timestamped by a human who watched it happen
 * rather than inferred from a sensor trace. */
enum class RealLifeMarkerType { ROLL_START, LIFT_OFF, FIFTEEN_M }

/** No `@Index("logId")` here unlike the sample tables — a log gets at most a handful of markers
 * (three buttons, tapped once each), nowhere near the row volume that makes an unindexed lookup
 * a real cost. */
@Entity(tableName = "real_life_markers")
data class RealLifeMarkerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val logId: Long,
    val epochMs: Long,
    val elapsedRealtimeNanos: Long,
    /** [RealLifeMarkerType.name]. */
    val markerType: String
)

@Dao
interface RealLifeMarkerDao {
    @Insert
    suspend fun insert(marker: RealLifeMarkerEntity): Long

    @Query("SELECT * FROM real_life_markers WHERE logId = :logId ORDER BY epochMs ASC")
    suspend fun getByLog(logId: Long): List<RealLifeMarkerEntity>

    @Query("DELETE FROM real_life_markers WHERE logId = :logId")
    suspend fun deleteByLogId(logId: Long)
}
