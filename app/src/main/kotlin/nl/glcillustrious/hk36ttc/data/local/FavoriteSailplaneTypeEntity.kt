package nl.glcillustrious.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Which sailplane types (by name, matched against sailplane_types.json) the user has marked as
 * a favorite for quick-fill in Sleepvlucht. Lives in Room rather than the JSON reference file
 * itself, so "standaardwaarden herstellen" of the calculation data never wipes this choice —
 * see rekenlogica.md §2.3b.
 */
@Entity(tableName = "favorite_sailplane_types")
data class FavoriteSailplaneTypeEntity(
    @PrimaryKey val name: String
)

@Dao
interface FavoriteSailplaneTypeDao {
    @Query("SELECT * FROM favorite_sailplane_types")
    fun observeAll(): Flow<List<FavoriteSailplaneTypeEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: FavoriteSailplaneTypeEntity)

    @Query("DELETE FROM favorite_sailplane_types WHERE name = :name")
    suspend fun delete(name: String)
}
