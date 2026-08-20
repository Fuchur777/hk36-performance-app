package nl.schellenberg.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

enum class FlightContextMode { AIRFIELD, MANUAL }

/** How today's grass actually is — deliberately separate from [RunwayStripEntity.surface],
 * which only records asphalt-vs-grass as a fixed property of the strip. Irrelevant/ignored
 * when the chosen runway's surface is [RunwaySurfaceType.ASPHALT]. */
enum class GrassCondition { DRY, WET, SOFT }

/**
 * The Vliegveld-vs-Handmatig choice and, when in airfield mode, which airfield — shared by
 * take-off, landing and sleepvlucht for one registration, so picking a field for the take-off
 * calculation also has it ready for landing without re-selecting. Which *runway direction* is
 * used is tracked per screen instead (`chosenRunwayDesignator` on each `*InputEntity`), since
 * the ranking differs per calculation.
 */
@Entity(tableName = "flight_contexts")
data class FlightContextEntity(
    @PrimaryKey val profileId: Long,
    val mode: String,
    val airfieldId: Long?,
    val grassCondition: String
)

@Dao
interface FlightContextDao {
    @Query("SELECT * FROM flight_contexts WHERE profileId = :profileId")
    suspend fun get(profileId: Long): FlightContextEntity?

    @Upsert
    suspend fun upsert(entity: FlightContextEntity)

    @Query("DELETE FROM flight_contexts WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: Long)
}
