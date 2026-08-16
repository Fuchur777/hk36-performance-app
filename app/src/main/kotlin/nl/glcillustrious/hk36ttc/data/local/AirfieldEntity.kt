package nl.glcillustrious.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A saved airfield — see docs/data/airfield_profile_schema.json for the field-by-field
 * rationale (this is the on-device, per-user equivalent of that template).
 *
 * [icao] is the field's own ICAO code, shown to the pilot; [metarStationIcao] is the station
 * a METAR should actually be read from, and is deliberately a *separate* field because most
 * Dutch gliding sites (Terlet, Malden, ...) have no METAR station of their own and rely on a
 * nearby one (e.g. Terlet -> EHDL). Leave it blank to fall back to [icao].
 *
 * [metarRaw]/[metarEnteredAtEpochMs] hold the last METAR text the pilot pasted in here (see
 * rekenlogica.md §5/§9) — entering it is a manual step for now; a future round adds an online
 * lookup that fills these same two fields instead.
 */
@Entity(tableName = "airfields")
data class AirfieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icao: String?,
    val metarStationIcao: String?,
    val elevationM: Double,
    val metarRaw: String?,
    val metarEnteredAtEpochMs: Long?
)

@Dao
interface AirfieldDao {
    @Query("SELECT * FROM airfields ORDER BY name ASC")
    fun observeAll(): Flow<List<AirfieldEntity>>

    @Query("SELECT * FROM airfields WHERE id = :id")
    suspend fun getById(id: Long): AirfieldEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(airfield: AirfieldEntity): Long

    @Update
    suspend fun update(airfield: AirfieldEntity)

    @Delete
    suspend fun delete(airfield: AirfieldEntity)
}
