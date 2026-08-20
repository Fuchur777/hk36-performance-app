package nl.schellenberg.hk36ttc.data.catalog

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One runway strip from the OurAirports catalogue — reference data, never user data. Shares
 * [AirportCatalogDatabase] with [AirportCatalogEntity] and is joined to it on
 * [airportIdent] = `AirportCatalogEntity.ident`.
 *
 * Mirrors the app's own `RunwayStripEntity` field for field (one row = one physical strip =
 * two usable directions), minus the slope: no public dataset publishes runway slope, so it
 * stays 0 until the pilot enters it.
 *
 * [headingDerived] records that [headingDegTrueA] was reconstructed from the runway number
 * because the source had no true heading. That is a rounded *magnetic* heading standing in for
 * a true one, so it must stay visible all the way to the pilot — see
 * [nl.schellenberg.hk36ttc.core.airport.parseRunwaysCsv].
 */
@Entity(
    tableName = "runway_catalog",
    indices = [Index("airportIdent")]
)
data class RunwayCatalogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val airportIdent: String,
    val designatorA: String,
    val designatorB: String?,
    val headingDegTrueA: Double,
    val headingDerived: Boolean,
    val lengthM: Double,
    /** `ASPHALT` or `GRASS`, stored as the name of the app's own `RunwaySurfaceType`. */
    val surface: String,
    val oneWay: Boolean
)

@Dao
interface RunwayCatalogDao {

    @Query("SELECT * FROM runway_catalog WHERE airportIdent = :airportIdent ORDER BY lengthM DESC")
    suspend fun forAirport(airportIdent: String): List<RunwayCatalogEntity>

    @Query("SELECT COUNT(*) FROM runway_catalog")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(runways: List<RunwayCatalogEntity>)

    @Query("DELETE FROM runway_catalog")
    suspend fun clear()
}
