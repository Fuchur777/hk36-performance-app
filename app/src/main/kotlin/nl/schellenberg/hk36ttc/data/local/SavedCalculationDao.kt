package nl.schellenberg.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCalculationDao {

    @Query("SELECT * FROM saved_calculations WHERE profileId = :profileId AND type = :type ORDER BY createdAtEpochMs DESC")
    fun observeByProfileAndType(profileId: Long, type: String): Flow<List<SavedCalculationEntity>>

    @Insert
    suspend fun insert(calculation: SavedCalculationEntity): Long

    @Delete
    suspend fun delete(calculation: SavedCalculationEntity)

    @Query("DELETE FROM saved_calculations WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: Long)
}
