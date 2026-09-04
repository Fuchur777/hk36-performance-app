package nl.schellenberg.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AircraftProfileDao {

    // Positioned profiles (sortOrder set by a homescreen drag) sort first by that position;
    // every other profile — including one that has never been dragged — falls back to
    // alphabetical, both among themselves and as the tiebreak for equal sortOrder.
    @Query(
        "SELECT * FROM aircraft_profiles " +
            "ORDER BY CASE WHEN sortOrder IS NULL THEN 1 ELSE 0 END, sortOrder ASC, registration ASC"
    )
    fun observeAll(): Flow<List<AircraftProfileEntity>>

    @Query("SELECT * FROM aircraft_profiles WHERE id = :id")
    suspend fun getById(id: Long): AircraftProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: AircraftProfileEntity): Long

    @Update
    suspend fun update(profile: AircraftProfileEntity)

    @Query("UPDATE aircraft_profiles SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Long)

    @Delete
    suspend fun delete(profile: AircraftProfileEntity)
}
