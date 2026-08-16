package nl.glcillustrious.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Which saved airfields the pilot has marked as a favorite, to keep the airfield picker on the
 * calculation screens short — mirrors [FavoriteSailplaneTypeEntity], but keyed by the airfield's
 * own row id rather than a name, since airfields (unlike sailplane types) are user-owned rows
 * with a stable id already. */
@Entity(tableName = "favorite_airfields")
data class FavoriteAirfieldEntity(
    @PrimaryKey val airfieldId: Long
)

@Dao
interface FavoriteAirfieldDao {
    @Query("SELECT * FROM favorite_airfields")
    fun observeAll(): Flow<List<FavoriteAirfieldEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FavoriteAirfieldEntity)

    /** Also used to cascade-clean up when the airfield itself is deleted. */
    @Query("DELETE FROM favorite_airfields WHERE airfieldId = :airfieldId")
    suspend fun deleteByAirfieldId(airfieldId: Long)
}
