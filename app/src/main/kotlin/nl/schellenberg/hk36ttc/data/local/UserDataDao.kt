package nl.schellenberg.hk36ttc.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Bulk read/replace access across every table that holds user-entered data, used only by the
 * export/import feature (`data/export/`).
 *
 * Kept as one separate DAO rather than scattering `getAll`/`insertAll`/`deleteAll` over the
 * eleven existing ones: those are shaped around what a single screen needs, and adding
 * wipe-the-table methods to each would put a destructive operation within easy reach of code
 * that has no business calling it. Here it is obvious what this is for.
 *
 * Purely new queries against existing tables — **no schema change, so no Room migration.**
 *
 * Insert uses REPLACE so an import can run without caring whether a wipe left anything behind;
 * the ids come straight from the backup file, which is what keeps every cross-table reference
 * (runway strips to airfields, inputs to profiles) valid.
 */
@Dao
interface UserDataDao {

    // --- Read everything --------------------------------------------------------------

    @Query("SELECT * FROM aircraft_profiles")
    suspend fun allProfiles(): List<AircraftProfileEntity>

    @Query("SELECT * FROM favorite_sailplane_types")
    suspend fun allFavoriteSailplaneTypes(): List<FavoriteSailplaneTypeEntity>

    @Query("SELECT * FROM airfields")
    suspend fun allAirfields(): List<AirfieldEntity>

    @Query("SELECT * FROM runway_strips")
    suspend fun allRunwayStrips(): List<RunwayStripEntity>

    @Query("SELECT * FROM favorite_airfields")
    suspend fun allFavoriteAirfields(): List<FavoriteAirfieldEntity>

    @Query("SELECT * FROM flight_contexts")
    suspend fun allFlightContexts(): List<FlightContextEntity>

    @Query("SELECT * FROM wb_inputs")
    suspend fun allWbInputs(): List<WbInputEntity>

    @Query("SELECT * FROM takeoff_inputs")
    suspend fun allTakeoffInputs(): List<TakeoffInputEntity>

    @Query("SELECT * FROM landing_inputs")
    suspend fun allLandingInputs(): List<LandingInputEntity>

    @Query("SELECT * FROM sleepvlucht_inputs")
    suspend fun allSleepvluchtInputs(): List<SleepvluchtInputEntity>

    @Query("SELECT * FROM last_wb_results")
    suspend fun allLastWbResults(): List<LastWbResultEntity>

    @Query("SELECT * FROM real_life_logs")
    suspend fun allRealLifeLogs(): List<RealLifeLogEntity>

    @Query("SELECT * FROM location_samples")
    suspend fun allLocationSamples(): List<LocationSampleEntity>

    @Query("SELECT * FROM imu_samples")
    suspend fun allImuSamples(): List<ImuSampleEntity>

    @Query("SELECT * FROM barometer_samples")
    suspend fun allBarometerSamples(): List<BarometerSampleEntity>

    @Query("SELECT * FROM real_life_markers")
    suspend fun allRealLifeMarkers(): List<RealLifeMarkerEntity>

    // --- Replace everything -----------------------------------------------------------

    @Query("DELETE FROM aircraft_profiles")
    suspend fun clearProfiles()

    @Query("DELETE FROM favorite_sailplane_types")
    suspend fun clearFavoriteSailplaneTypes()

    @Query("DELETE FROM airfields")
    suspend fun clearAirfields()

    @Query("DELETE FROM runway_strips")
    suspend fun clearRunwayStrips()

    @Query("DELETE FROM favorite_airfields")
    suspend fun clearFavoriteAirfields()

    @Query("DELETE FROM flight_contexts")
    suspend fun clearFlightContexts()

    @Query("DELETE FROM wb_inputs")
    suspend fun clearWbInputs()

    @Query("DELETE FROM takeoff_inputs")
    suspend fun clearTakeoffInputs()

    @Query("DELETE FROM landing_inputs")
    suspend fun clearLandingInputs()

    @Query("DELETE FROM sleepvlucht_inputs")
    suspend fun clearSleepvluchtInputs()

    @Query("DELETE FROM last_wb_results")
    suspend fun clearLastWbResults()

    /** Deleted before the header table above it -- Room has no FK constraints on this schema, so
     * order has no correctness effect on its own, but clearing the child rows first (and
     * inserting them last, below) mirrors the shape a real FK-cascading schema would require and
     * keeps this one straightforward to reason about. */
    @Query("DELETE FROM real_life_logs")
    suspend fun clearRealLifeLogs()

    @Query("DELETE FROM location_samples")
    suspend fun clearLocationSamples()

    @Query("DELETE FROM imu_samples")
    suspend fun clearImuSamples()

    @Query("DELETE FROM barometer_samples")
    suspend fun clearBarometerSamples()

    @Query("DELETE FROM real_life_markers")
    suspend fun clearRealLifeMarkers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(rows: List<AircraftProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteSailplaneTypes(rows: List<FavoriteSailplaneTypeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAirfields(rows: List<AirfieldEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunwayStrips(rows: List<RunwayStripEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteAirfields(rows: List<FavoriteAirfieldEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlightContexts(rows: List<FlightContextEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWbInputs(rows: List<WbInputEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTakeoffInputs(rows: List<TakeoffInputEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandingInputs(rows: List<LandingInputEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepvluchtInputs(rows: List<SleepvluchtInputEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLastWbResults(rows: List<LastWbResultEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRealLifeLogs(rows: List<RealLifeLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationSamples(rows: List<LocationSampleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImuSamples(rows: List<ImuSampleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarometerSamples(rows: List<BarometerSampleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRealLifeMarkers(rows: List<RealLifeMarkerEntity>)
}
