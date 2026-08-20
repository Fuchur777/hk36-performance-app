package nl.schellenberg.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The most recent W&B total mass computed for a registration, so other modules (sleepvlucht:
 * "sleepvliegtuig-gewicht") can default to "what this aircraft actually weighs right now"
 * instead of a separate, easily-stale manual entry. Overwritten on every W&B recalculation —
 * this is a live cache, not a flight log.
 */
@Entity(tableName = "last_wb_results")
data class LastWbResultEntity(
    @PrimaryKey val profileId: Long,
    val totalMassKg: Double,
    val computedAtEpochMs: Long
)

@Dao
interface LastWbResultDao {
    @Query("SELECT * FROM last_wb_results WHERE profileId = :profileId")
    fun observe(profileId: Long): Flow<LastWbResultEntity?>

    @Query("SELECT * FROM last_wb_results WHERE profileId = :profileId")
    suspend fun get(profileId: Long): LastWbResultEntity?

    @Upsert
    suspend fun upsert(entity: LastWbResultEntity)

    @Query("DELETE FROM last_wb_results WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: Long)
}
