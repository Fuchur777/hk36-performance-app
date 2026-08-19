package nl.glcillustrious.hk36ttc.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.glcillustrious.hk36ttc.core.wb.AircraftProfile

class AircraftProfileRepository(
    private val dao: AircraftProfileDao,
    private val lastWbResultDao: LastWbResultDao,
    private val favoriteSailplaneTypeDao: FavoriteSailplaneTypeDao,
    private val wbInputDao: WbInputDao,
    private val takeoffInputDao: TakeoffInputDao,
    private val landingInputDao: LandingInputDao,
    private val sleepvluchtInputDao: SleepvluchtInputDao,
    private val airfieldDao: AirfieldDao,
    private val runwayStripDao: RunwayStripDao,
    private val flightContextDao: FlightContextDao,
    private val favoriteAirfieldDao: FavoriteAirfieldDao,
    /** Runs [block] in one database transaction. A lambda rather than the `AppDatabase` itself
     * so this class stays testable on the JVM — the same shape [nl.glcillustrious.hk36ttc.data.export.UserDataRepository]
     * and [nl.glcillustrious.hk36ttc.data.catalog.AirportCatalogRepository] already take. */
    private val transaction: suspend (suspend () -> Unit) -> Unit
) {

    fun observeAll(): Flow<List<AircraftProfileEntity>> = dao.observeAll()

    fun observeAllDomain(): Flow<List<AircraftProfile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): AircraftProfileEntity? = dao.getById(id)

    suspend fun save(profile: AircraftProfile, id: Long = 0): Long =
        if (id == 0L) dao.insert(profile.toEntity()) else {
            dao.update(profile.toEntity(id))
            id
        }

    suspend fun delete(profile: AircraftProfileEntity) = dao.delete(profile)

    /** Deletes a registration and every per-registration calculation input tied to it (W&B,
     * Take-off, Landing, Sleepvlucht, last W&B result, flight context), so removing a
     * registration doesn't leave orphaned rows behind under a profileId that will never be
     * reused. Saved airfields themselves are NOT touched — they belong to no single
     * registration.
     *
     * One transaction: a process kill part-way through used to leave the profile gone but its
     * saved inputs behind, under a profileId that is never reused — invisible to the pilot, and
     * carried along in every backup from then on. */
    suspend fun deleteProfileCascade(profile: AircraftProfileEntity) = transaction {
        dao.delete(profile)
        lastWbResultDao.deleteByProfileId(profile.id)
        wbInputDao.deleteByProfileId(profile.id)
        takeoffInputDao.deleteByProfileId(profile.id)
        landingInputDao.deleteByProfileId(profile.id)
        sleepvluchtInputDao.deleteByProfileId(profile.id)
        flightContextDao.deleteByProfileId(profile.id)
    }

    /** Called after every W&B recalculation so other modules can read "what this aircraft
     * actually weighs right now" instead of a separate, easily-stale manual entry. */
    suspend fun saveLastWbResult(profileId: Long, totalMassKg: Double) =
        lastWbResultDao.upsert(LastWbResultEntity(profileId, totalMassKg, System.currentTimeMillis()))

    suspend fun getLastWbResult(profileId: Long): LastWbResultEntity? = lastWbResultDao.get(profileId)

    fun observeFavoriteSailplaneTypeNames(): Flow<List<String>> =
        favoriteSailplaneTypeDao.observeAll().map { list -> list.map { it.name } }

    suspend fun setSailplaneTypeFavorite(name: String, favorite: Boolean) {
        if (favorite) favoriteSailplaneTypeDao.insert(FavoriteSailplaneTypeEntity(name))
        else favoriteSailplaneTypeDao.delete(name)
    }

    /** Last-entered form values per registration, so reopening a calculation screen for the
     * same registration shows what was last typed there instead of resetting to defaults. */
    suspend fun getWbInput(profileId: Long): WbInputEntity? = wbInputDao.get(profileId)
    suspend fun saveWbInput(entity: WbInputEntity) = wbInputDao.upsert(entity)

    suspend fun getTakeoffInput(profileId: Long): TakeoffInputEntity? = takeoffInputDao.get(profileId)
    suspend fun saveTakeoffInput(entity: TakeoffInputEntity) = takeoffInputDao.upsert(entity)

    suspend fun getLandingInput(profileId: Long): LandingInputEntity? = landingInputDao.get(profileId)
    suspend fun saveLandingInput(entity: LandingInputEntity) = landingInputDao.upsert(entity)

    suspend fun getSleepvluchtInput(profileId: Long): SleepvluchtInputEntity? = sleepvluchtInputDao.get(profileId)
    suspend fun saveSleepvluchtInput(entity: SleepvluchtInputEntity) = sleepvluchtInputDao.upsert(entity)

    // --- Airfields / runway strips (Fase 2c) ---

    fun observeAirfields(): Flow<List<AirfieldEntity>> = airfieldDao.observeAll()

    suspend fun getAirfield(id: Long): AirfieldEntity? = airfieldDao.getById(id)

    suspend fun saveAirfield(airfield: AirfieldEntity): Long =
        if (airfield.id == 0L) airfieldDao.insert(airfield) else {
            airfieldDao.update(airfield)
            airfield.id
        }

    /** Deletes an airfield and every runway strip that belongs to it — a strip has no meaning
     * without its airfield. Registrations whose [FlightContextEntity] pointed at this airfield
     * are left as-is (their `airfieldId` becomes dangling); the calculation screens fall back
     * to Handmatig when the referenced airfield can no longer be found.
     *
     * One transaction, for the same reason as [deleteProfileCascade]. */
    suspend fun deleteAirfieldCascade(airfield: AirfieldEntity) = transaction {
        runwayStripDao.deleteByAirfieldId(airfield.id)
        favoriteAirfieldDao.deleteByAirfieldId(airfield.id)
        airfieldDao.delete(airfield)
    }

    fun observeFavoriteAirfieldIds(): Flow<List<Long>> =
        favoriteAirfieldDao.observeAll().map { list -> list.map { it.airfieldId } }

    suspend fun setAirfieldFavorite(airfieldId: Long, favorite: Boolean) {
        if (favorite) favoriteAirfieldDao.insert(FavoriteAirfieldEntity(airfieldId))
        else favoriteAirfieldDao.deleteByAirfieldId(airfieldId)
    }

    fun observeRunwayStrips(airfieldId: Long): Flow<List<RunwayStripEntity>> = runwayStripDao.observeByAirfield(airfieldId)

    suspend fun getRunwayStrips(airfieldId: Long): List<RunwayStripEntity> = runwayStripDao.getByAirfield(airfieldId)

    suspend fun saveRunwayStrip(strip: RunwayStripEntity): Long =
        if (strip.id == 0L) runwayStripDao.insert(strip) else {
            runwayStripDao.update(strip)
            strip.id
        }

    suspend fun deleteRunwayStrip(strip: RunwayStripEntity) = runwayStripDao.delete(strip)

    // --- Flight context (Fase 2c): Vliegveld/Handmatig choice shared by take-off/landing/sleepvlucht ---

    suspend fun getFlightContext(profileId: Long): FlightContextEntity? = flightContextDao.get(profileId)
    suspend fun saveFlightContext(entity: FlightContextEntity) = flightContextDao.upsert(entity)
}
