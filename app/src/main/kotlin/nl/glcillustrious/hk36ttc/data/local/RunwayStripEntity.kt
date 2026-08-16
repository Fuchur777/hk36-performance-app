package nl.glcillustrious.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** What's physically underfoot on a [RunwayStripEntity] — whether that grass is currently dry,
 * wet, or soft is a per-flight condition, not a property of the strip itself (see
 * [FlightContextEntity.grassCondition]), because the same strip can be either on different
 * days. */
enum class RunwaySurfaceType { ASPHALT, GRASS }

/**
 * One physical runway strip, usable from either end. Storing both directions of the *same*
 * strip as a single row — rather than two independent runway rows — makes it structurally
 * impossible for e.g. "03" and "21" to drift apart on length or surface, and means
 * adding/removing a runway always adds/removes both directions together.
 *
 * [headingDegTrueA] is the TRUE heading of [designatorA] (METAR wind direction is also true,
 * never magnetic — see rekenlogica.md §8). Direction B's heading is `headingDegTrueA + 180`;
 * [slopePctA] is B's slope with the sign flipped (uphill one way is downhill the other way on
 * the same physical strip).
 */
@Entity(tableName = "runway_strips")
data class RunwayStripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val airfieldId: Long,
    val designatorA: String,
    val designatorB: String,
    val headingDegTrueA: Double,
    val lengthM: Double,
    val surface: String,
    val slopePctA: Double
)

@Dao
interface RunwayStripDao {
    @Query("SELECT * FROM runway_strips WHERE airfieldId = :airfieldId ORDER BY id ASC")
    fun observeByAirfield(airfieldId: Long): Flow<List<RunwayStripEntity>>

    @Query("SELECT * FROM runway_strips WHERE airfieldId = :airfieldId ORDER BY id ASC")
    suspend fun getByAirfield(airfieldId: Long): List<RunwayStripEntity>

    @Insert
    suspend fun insert(strip: RunwayStripEntity): Long

    @Update
    suspend fun update(strip: RunwayStripEntity)

    @Delete
    suspend fun delete(strip: RunwayStripEntity)

    @Query("DELETE FROM runway_strips WHERE airfieldId = :airfieldId")
    suspend fun deleteByAirfieldId(airfieldId: Long)
}
