package nl.glcillustrious.hk36ttc.data.catalog

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Where the currently loaded catalogue came from — shown to the pilot so "how old is this?" has an answer. */
enum class CatalogSource { BUNDLED, DOWNLOADED }

/**
 * Single-row table recording which catalogue is currently loaded. Kept in the catalogue
 * database itself rather than in preferences so that wiping the catalogue and forgetting its
 * provenance can never come apart.
 */
@Entity(tableName = "catalog_meta")
data class CatalogMetaEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val source: String,
    val loadedAtEpochMs: Long,
    val airportCount: Int,
    val runwayCount: Int
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

@Dao
interface CatalogMetaDao {

    @Query("SELECT * FROM catalog_meta WHERE id = :id")
    suspend fun get(id: Int = CatalogMetaEntity.SINGLETON_ID): CatalogMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: CatalogMetaEntity)
}
