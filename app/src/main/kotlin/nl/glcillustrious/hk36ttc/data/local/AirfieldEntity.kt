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
 * [metarRaw]/[metarEnteredAtEpochMs] hold the last METAR fetched for this airfield (see
 * rekenlogica.md §5c/§9). Since Fase 2c ronde 6 these are filled by the online lookup only —
 * there is no hand-entered METAR text anymore, just the station to read it from.
 */
@Entity(tableName = "airfields")
data class AirfieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icao: String?,
    val metarStationIcao: String?,
    val elevationM: Double,
    /** False when the elevation is a placeholder rather than a known value — set by the
     * catalogue import when `elevation_ft` was empty. A plain 0.0 cannot carry this by itself:
     * several Dutch fields really do sit at or below sea level, so "0 m" is indistinguishable
     * from "not recorded" without this flag, and the difference feeds straight into the
     * pressure-altitude derivation and from there into every distance. */
    val elevationKnown: Boolean = true,
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
