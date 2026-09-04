package nl.schellenberg.hk36ttc.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.schellenberg.hk36ttc.core.wb.AircraftProfile

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
    private val realLifeLogDao: RealLifeLogDao,
    private val locationSampleDao: LocationSampleDao,
    private val imuSampleDao: ImuSampleDao,
    private val barometerSampleDao: BarometerSampleDao,
    private val realLifeMarkerDao: RealLifeMarkerDao,
    private val savedCalculationDao: SavedCalculationDao,
    /** Runs [block] in one database transaction. A lambda rather than the `AppDatabase` itself
     * so this class stays testable on the JVM — the same shape [nl.schellenberg.hk36ttc.data.export.UserDataRepository]
     * and [nl.schellenberg.hk36ttc.data.catalog.AirportCatalogRepository] already take. */
    private val transaction: suspend (suspend () -> Unit) -> Unit
) {

    fun observeAll(): Flow<List<AircraftProfileEntity>> = dao.observeAll()

    fun observeAllDomain(): Flow<List<AircraftProfile>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): AircraftProfileEntity? = dao.getById(id)

    suspend fun save(profile: AircraftProfile, id: Long = 0): Long =
        if (id == 0L) {
            dao.insert(profile.toEntity())
        } else {
            // toEntity() has no way to know the row's current sortOrder — look it up so an
            // edit-and-save never undoes a manual homescreen drag by silently resetting it.
            dao.update(profile.toEntity(id, sortOrder = dao.getById(id)?.sortOrder))
            id
        }

    suspend fun delete(profile: AircraftProfileEntity) = dao.delete(profile)

    /** Persists a full manual reorder of the homescreen list: [orderedIds] is every profile id
     * in its new top-to-bottom order. Assigns sequential positions rather than nudging just the
     * dragged row, since a plain drag-and-drop only ever reports "here is the whole list now" —
     * see [nl.schellenberg.hk36ttc.ui.profile.ProfileListScreen]. */
    suspend fun reorderProfiles(orderedIds: List<Long>) = transaction {
        orderedIds.forEachIndexed { index, id -> dao.updateSortOrder(id, index.toLong()) }
    }

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
        realLifeLogDao.getIdsByProfileId(profile.id).forEach { logId ->
            locationSampleDao.deleteByLogId(logId)
            imuSampleDao.deleteByLogId(logId)
            barometerSampleDao.deleteByLogId(logId)
            realLifeMarkerDao.deleteByLogId(logId)
        }
        realLifeLogDao.deleteByProfileId(profile.id)
        savedCalculationDao.deleteByProfileId(profile.id)
    }

    // --- Saved calculations (W&B/Take-off/Glider tow/Landing "Save" action) ---

    fun observeSavedCalculations(profileId: Long, type: SavedCalculationType): Flow<List<SavedCalculationEntity>> =
        savedCalculationDao.observeByProfileAndType(profileId, type.name)

    suspend fun saveCalculation(calculation: SavedCalculationEntity): Long = savedCalculationDao.insert(calculation)

    suspend fun deleteCalculation(calculation: SavedCalculationEntity) = savedCalculationDao.delete(calculation)

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

    // --- Real Life Performance (Fase 4a): raw GPS/IMU/barometer recordings ---

    fun observeRealLifeLogs(profileId: Long): Flow<List<RealLifeLogEntity>> = realLifeLogDao.observeByProfile(profileId)

    suspend fun getRealLifeLog(logId: Long): RealLifeLogEntity? = realLifeLogDao.getById(logId)

    suspend fun startRealLifeLog(log: RealLifeLogEntity): Long = realLifeLogDao.insert(log)

    suspend fun stopRealLifeLog(logId: Long, stoppedAt: Long, reason: String) =
        realLifeLogDao.markStopped(logId, stoppedAt, reason)

    /** Saves the Fase 4c conditions snapshot (surface/slope/weather) onto an existing log --
     * always called well after the recording itself, via the detail screen's edit flow. */
    suspend fun updateRealLifeLog(log: RealLifeLogEntity) = realLifeLogDao.update(log)

    suspend fun appendLocationSamples(samples: List<LocationSampleEntity>) = locationSampleDao.insertAll(samples)
    suspend fun appendImuSamples(samples: List<ImuSampleEntity>) = imuSampleDao.insertAll(samples)
    suspend fun appendBarometerSamples(samples: List<BarometerSampleEntity>) = barometerSampleDao.insertAll(samples)

    suspend fun getLocationSamples(logId: Long): List<LocationSampleEntity> = locationSampleDao.getByLog(logId)
    suspend fun getImuSamples(logId: Long): List<ImuSampleEntity> = imuSampleDao.getByLog(logId)
    suspend fun getBarometerSamples(logId: Long): List<BarometerSampleEntity> = barometerSampleDao.getByLog(logId)

    suspend fun countLocationSamples(logId: Long): Int = locationSampleDao.countByLog(logId)
    suspend fun countImuSamples(logId: Long): Int = imuSampleDao.countByLog(logId)
    suspend fun countBarometerSamples(logId: Long): Int = barometerSampleDao.countByLog(logId)

    /** Ground-truth events a co-pilot/observer taps during recording — see [RealLifeMarkerEntity]. */
    suspend fun addRealLifeMarker(marker: RealLifeMarkerEntity): Long = realLifeMarkerDao.insert(marker)
    suspend fun getRealLifeMarkers(logId: Long): List<RealLifeMarkerEntity> = realLifeMarkerDao.getByLog(logId)

    /** One transaction, same reasoning as [deleteProfileCascade]/[deleteAirfieldCascade]: a
     * recording's sample and marker tables must never survive without their header row. */
    suspend fun deleteRealLifeLogCascade(log: RealLifeLogEntity) = transaction {
        locationSampleDao.deleteByLogId(log.id)
        imuSampleDao.deleteByLogId(log.id)
        barometerSampleDao.deleteByLogId(log.id)
        realLifeMarkerDao.deleteByLogId(log.id)
        realLifeLogDao.delete(log)
    }
}
