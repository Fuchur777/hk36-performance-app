package nl.glcillustrious.hk36ttc.data.export

import nl.glcillustrious.hk36ttc.data.local.AircraftProfileEntity
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FavoriteAirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FavoriteSailplaneTypeEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextEntity
import nl.glcillustrious.hk36ttc.data.local.LandingInputEntity
import nl.glcillustrious.hk36ttc.data.local.LastWbResultEntity
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.SleepvluchtInputEntity
import nl.glcillustrious.hk36ttc.data.local.TakeoffInputEntity
import nl.glcillustrious.hk36ttc.data.local.UserDataDao
import nl.glcillustrious.hk36ttc.data.local.WbInputEntity

/**
 * In-memory [UserDataDao], matching the hand-written fakes in `data/local/FakeDaos.kt`.
 *
 * Insert appends rather than upserting by key: the import always clears first, and a test that
 * accidentally produced duplicates should fail loudly rather than have them silently collapse.
 */
class FakeUserDataDao : UserDataDao {
    val profiles = mutableListOf<AircraftProfileEntity>()
    val favoriteSailplaneTypes = mutableListOf<FavoriteSailplaneTypeEntity>()
    val airfields = mutableListOf<AirfieldEntity>()
    val runwayStrips = mutableListOf<RunwayStripEntity>()
    val favoriteAirfields = mutableListOf<FavoriteAirfieldEntity>()
    val flightContexts = mutableListOf<FlightContextEntity>()
    val wbInputs = mutableListOf<WbInputEntity>()
    val takeoffInputs = mutableListOf<TakeoffInputEntity>()
    val landingInputs = mutableListOf<LandingInputEntity>()
    val sleepvluchtInputs = mutableListOf<SleepvluchtInputEntity>()
    val lastWbResults = mutableListOf<LastWbResultEntity>()

    override suspend fun allProfiles() = profiles.toList()
    override suspend fun allFavoriteSailplaneTypes() = favoriteSailplaneTypes.toList()
    override suspend fun allAirfields() = airfields.toList()
    override suspend fun allRunwayStrips() = runwayStrips.toList()
    override suspend fun allFavoriteAirfields() = favoriteAirfields.toList()
    override suspend fun allFlightContexts() = flightContexts.toList()
    override suspend fun allWbInputs() = wbInputs.toList()
    override suspend fun allTakeoffInputs() = takeoffInputs.toList()
    override suspend fun allLandingInputs() = landingInputs.toList()
    override suspend fun allSleepvluchtInputs() = sleepvluchtInputs.toList()
    override suspend fun allLastWbResults() = lastWbResults.toList()

    override suspend fun clearProfiles() = profiles.clear()
    override suspend fun clearFavoriteSailplaneTypes() = favoriteSailplaneTypes.clear()
    override suspend fun clearAirfields() = airfields.clear()
    override suspend fun clearRunwayStrips() = runwayStrips.clear()
    override suspend fun clearFavoriteAirfields() = favoriteAirfields.clear()
    override suspend fun clearFlightContexts() = flightContexts.clear()
    override suspend fun clearWbInputs() = wbInputs.clear()
    override suspend fun clearTakeoffInputs() = takeoffInputs.clear()
    override suspend fun clearLandingInputs() = landingInputs.clear()
    override suspend fun clearSleepvluchtInputs() = sleepvluchtInputs.clear()
    override suspend fun clearLastWbResults() = lastWbResults.clear()

    override suspend fun insertProfiles(rows: List<AircraftProfileEntity>) { profiles += rows }
    override suspend fun insertFavoriteSailplaneTypes(rows: List<FavoriteSailplaneTypeEntity>) { favoriteSailplaneTypes += rows }
    override suspend fun insertAirfields(rows: List<AirfieldEntity>) { airfields += rows }
    override suspend fun insertRunwayStrips(rows: List<RunwayStripEntity>) { runwayStrips += rows }
    override suspend fun insertFavoriteAirfields(rows: List<FavoriteAirfieldEntity>) { favoriteAirfields += rows }
    override suspend fun insertFlightContexts(rows: List<FlightContextEntity>) { flightContexts += rows }
    override suspend fun insertWbInputs(rows: List<WbInputEntity>) { wbInputs += rows }
    override suspend fun insertTakeoffInputs(rows: List<TakeoffInputEntity>) { takeoffInputs += rows }
    override suspend fun insertLandingInputs(rows: List<LandingInputEntity>) { landingInputs += rows }
    override suspend fun insertSleepvluchtInputs(rows: List<SleepvluchtInputEntity>) { sleepvluchtInputs += rows }
    override suspend fun insertLastWbResults(rows: List<LastWbResultEntity>) { lastWbResults += rows }
}
