package nl.schellenberg.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * The last-entered form values per registration for each calculation screen, so reopening the
 * same registration shows what was last typed there instead of resetting to the stepper
 * defaults. One row per profile per screen; overwritten on every change, not a history.
 */
@Entity(tableName = "wb_inputs")
data class WbInputEntity(
    @PrimaryKey val profileId: Long,
    val pilotKg: Int,
    val copilotKg: Int,
    val fuelLiters: Int,
    val baggageKg: Int
)

@Dao
interface WbInputDao {
    @Query("SELECT * FROM wb_inputs WHERE profileId = :profileId")
    suspend fun get(profileId: Long): WbInputEntity?

    @Upsert
    suspend fun upsert(entity: WbInputEntity)

    @Query("DELETE FROM wb_inputs WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: Long)
}

@Entity(tableName = "takeoff_inputs")
data class TakeoffInputEntity(
    @PrimaryKey val profileId: Long,
    val oatC: Int,
    val pressureAltM: Int,
    val headwindKts: Int,
    val surfaceType: String,
    val slopePct: Int,
    val marginFactorPct: Int,
    /** Runway direction the pilot explicitly locked in (e.g. "03"). `null` means "follow the
     * live [nl.schellenberg.hk36ttc.core.metar.RunwayAdvisor] recommendation" rather than a
     * fixed choice — only meaningful when [nl.schellenberg.hk36ttc.data.local.FlightContextEntity]
     * for this registration is in `AIRFIELD` mode. */
    val chosenRunwayDesignator: String? = null
)

@Dao
interface TakeoffInputDao {
    @Query("SELECT * FROM takeoff_inputs WHERE profileId = :profileId")
    suspend fun get(profileId: Long): TakeoffInputEntity?

    @Upsert
    suspend fun upsert(entity: TakeoffInputEntity)

    @Query("DELETE FROM takeoff_inputs WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: Long)
}

@Entity(tableName = "landing_inputs")
data class LandingInputEntity(
    @PrimaryKey val profileId: Long,
    val oatC: Int,
    val pressureAltM: Int,
    val surfaceType: String,
    val customSurfaceFactorPct: Int,
    val slopePct: Int,
    val marginFactorPct: Int,
    /** See [TakeoffInputEntity.chosenRunwayDesignator] — same meaning, tracked separately per
     * screen because the ranking (landing distance vs. take-off distance) differs. */
    val chosenRunwayDesignator: String? = null
)

@Dao
interface LandingInputDao {
    @Query("SELECT * FROM landing_inputs WHERE profileId = :profileId")
    suspend fun get(profileId: Long): LandingInputEntity?

    @Upsert
    suspend fun upsert(entity: LandingInputEntity)

    @Query("DELETE FROM landing_inputs WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: Long)
}

@Entity(tableName = "sleepvlucht_inputs")
data class SleepvluchtInputEntity(
    @PrimaryKey val profileId: Long,
    val oatC: Int,
    val pressureAltM: Int,
    val headwindKts: Int,
    val slopePct: Int,
    val marginFactorPct: Int,
    val sailplaneMassKg: Int,
    val ldRatioKnown: Boolean,
    val ldRatio: Int,
    val instructionFlight: Boolean,
    val surfaceType: String,
    val selectedSailplaneTypeName: String?,
    val selectedSailplaneTypeUsedFallback: Boolean,
    val towplaneMassManualOverride: Boolean,
    val towplaneMassManualKg: Int,
    /** See [TakeoffInputEntity.chosenRunwayDesignator] — ranked on tow take-off distance here. */
    val chosenRunwayDesignator: String? = null
)

@Dao
interface SleepvluchtInputDao {
    @Query("SELECT * FROM sleepvlucht_inputs WHERE profileId = :profileId")
    suspend fun get(profileId: Long): SleepvluchtInputEntity?

    @Upsert
    suspend fun upsert(entity: SleepvluchtInputEntity)

    @Query("DELETE FROM sleepvlucht_inputs WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: Long)
}
