package nl.schellenberg.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Which take-off the pilot picked before starting a recording — same distinction the
 * calculation screens already make, kept here as its own field since a log is not tied to any
 * particular calculation run. */
enum class RealLifeConfiguration { NORMAL, SLEEPVLUCHT }

/** Why a recording actually stopped, so a log's completeness is never ambiguous later.
 * [START_FAILED] is for when the log row was inserted but [nl.schellenberg.hk36ttc.ui.reallife.RealLifeRecorder.start]
 * never actually registered any listener (e.g. permission revoked between the gate and the tap) --
 * distinct from every other reason, all of which stop a recording that was genuinely running. */
enum class RealLifeStopReason { MANUAL, TIMEOUT, BACKGROUNDED, START_FAILED }

/** Where a recording's [RealLifeLogEntity.conditionsSource] weather/AFM-comparison snapshot came
 * from (Fase 4c) — always filled in after the fact on the detail screen, never during recording
 * itself. `METAR_HISTORICAL` is what backfills a past recording (e.g. via the Iowa Environmental
 * Mesonet ASOS archive); `METAR_LIVE` reuses the same current-METAR path the calc screens already
 * use; `MANUAL` is typed directly when no METAR station applies. */
enum class ConditionsSource { METAR_LIVE, METAR_HISTORICAL, MANUAL }

/**
 * Header row for one "Real Life Performance" recording (Fase 4a — see docs/00-plan.md).
 * [notes] is free text (device placement, surface, wind) rather than several structured fields,
 * so the pilot can capture whatever variety matters for later calibration without the app
 * guessing which fields to add.
 *
 * The conditions fields below (Fase 4c) are always filled in AFTER the recording, via the
 * detail screen's "Condities bewerken" flow, never during the recording itself — every field is
 * therefore nullable, and every existing recording reads back with all of them null until
 * backfilled. [airfieldId] is optional and, deliberately, is used only for its METAR station and
 * elevation — not for deriving surface/slope, which stay manually entered even when an airfield
 * is linked (see `docs/00-plan.md` Fase 4c: linking a runway strip would duplicate a whole
 * baan-kiezer UI for no real benefit, since the pilot already knows what surface/slope applied).
 * No `@ForeignKey` on [airfieldId] — this project never uses Room FK constraints (see
 * [FlightContextEntity.airfieldId] for the same pattern).
 */
@Entity(tableName = "real_life_logs")
data class RealLifeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val configuration: String,
    val notes: String,
    val startedAtEpochMs: Long,
    /** Null while a recording is still in progress. */
    val stoppedAtEpochMs: Long?,
    /** [RealLifeStopReason.name], null until [stoppedAtEpochMs] is set. */
    val stopReason: String?,
    val barometerAvailable: Boolean,
    val gpsRequestedIntervalMs: Long,
    val airfieldId: Long? = null,
    /** `TakeoffSurfaceType.name` — always manually chosen, even when [airfieldId] is set. */
    val surfaceType: String? = null,
    val slopePct: Double? = null,
    val oatC: Int? = null,
    val pressureAltM: Int? = null,
    val windDirectionDeg: Int? = null,
    val windSpeedKts: Int? = null,
    /** The raw METAR text actually used (live or historical), kept for traceability/audit. Null
     * when [conditionsSource] is [ConditionsSource.MANUAL]. */
    val metarRaw: String? = null,
    val metarObservedAtEpochMs: Long? = null,
    /** [ConditionsSource.name]. */
    val conditionsSource: String? = null
)

@Dao
interface RealLifeLogDao {
    @Query("SELECT * FROM real_life_logs WHERE profileId = :profileId ORDER BY startedAtEpochMs DESC")
    fun observeByProfile(profileId: Long): Flow<List<RealLifeLogEntity>>

    @Query("SELECT * FROM real_life_logs WHERE id = :logId")
    suspend fun getById(logId: Long): RealLifeLogEntity?

    @Insert
    suspend fun insert(log: RealLifeLogEntity): Long

    @Query("UPDATE real_life_logs SET stoppedAtEpochMs = :stoppedAt, stopReason = :reason WHERE id = :logId")
    suspend fun markStopped(logId: Long, stoppedAt: Long, reason: String)

    /** Saves the Fase 4c conditions snapshot -- always called well after recording, via the
     * detail screen's edit flow, never during the recording itself. */
    @Update
    suspend fun update(log: RealLifeLogEntity)

    @Delete
    suspend fun delete(log: RealLifeLogEntity)

    @Query("SELECT id FROM real_life_logs WHERE profileId = :profileId")
    suspend fun getIdsByProfileId(profileId: Long): List<Long>

    @Query("DELETE FROM real_life_logs WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: Long)
}
