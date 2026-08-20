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

    @Query("SELECT * FROM aircraft_profiles ORDER BY registration ASC")
    fun observeAll(): Flow<List<AircraftProfileEntity>>

    @Query("SELECT * FROM aircraft_profiles WHERE id = :id")
    suspend fun getById(id: Long): AircraftProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(profile: AircraftProfileEntity): Long

    @Update
    suspend fun update(profile: AircraftProfileEntity)

    @Delete
    suspend fun delete(profile: AircraftProfileEntity)
}
