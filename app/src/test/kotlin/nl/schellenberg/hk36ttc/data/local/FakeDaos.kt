package nl.schellenberg.hk36ttc.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-written in-memory fakes for the Room DAO interfaces, used to back a real
 * [AircraftProfileRepository] in pure-JVM ViewModel tests. Room DAOs are plain Kotlin
 * interfaces with no generated implementation required to compile against, so these avoid
 * pulling in Robolectric (and its own dependency-version risk) just to exercise a ViewModel.
 * See docs/00-plan.md §11 point 4.
 */
class FakeAircraftProfileDao : AircraftProfileDao {
    private val profiles = MutableStateFlow<List<AircraftProfileEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<AircraftProfileEntity>> = profiles

    override suspend fun getById(id: Long): AircraftProfileEntity? = profiles.value.find { it.id == id }

    override suspend fun insert(profile: AircraftProfileEntity): Long {
        val id = nextId++
        profiles.value = profiles.value + profile.copy(id = id)
        return id
    }

    override suspend fun update(profile: AircraftProfileEntity) {
        profiles.value = profiles.value.map { if (it.id == profile.id) profile else it }
    }

    override suspend fun delete(profile: AircraftProfileEntity) {
        profiles.value = profiles.value.filterNot { it.id == profile.id }
    }

    override suspend fun updateSortOrder(id: Long, sortOrder: Long) {
        profiles.value = profiles.value.map { if (it.id == id) it.copy(sortOrder = sortOrder) else it }
    }

    /** Test helper: seed a profile directly under its own id, bypassing [insert]'s auto-id. */
    fun seed(profile: AircraftProfileEntity) {
        profiles.value = profiles.value + profile
        nextId = maxOf(nextId, profile.id + 1)
    }
}

class FakeLastWbResultDao : LastWbResultDao {
    private val results = mutableMapOf<Long, LastWbResultEntity>()

    override fun observe(profileId: Long): Flow<LastWbResultEntity?> = MutableStateFlow(results[profileId])
    override suspend fun get(profileId: Long): LastWbResultEntity? = results[profileId]
    override suspend fun upsert(entity: LastWbResultEntity) { results[entity.profileId] = entity }
    override suspend fun deleteByProfileId(profileId: Long) { results.remove(profileId) }
}

class FakeFavoriteSailplaneTypeDao : FavoriteSailplaneTypeDao {
    private val favorites = MutableStateFlow<List<FavoriteSailplaneTypeEntity>>(emptyList())

    override fun observeAll(): Flow<List<FavoriteSailplaneTypeEntity>> = favorites

    override suspend fun insert(entity: FavoriteSailplaneTypeEntity) {
        if (favorites.value.none { it.name == entity.name }) favorites.value = favorites.value + entity
    }

    override suspend fun delete(name: String) {
        favorites.value = favorites.value.filterNot { it.name == name }
    }
}

class FakeWbInputDao : WbInputDao {
    private val inputs = mutableMapOf<Long, WbInputEntity>()
    override suspend fun get(profileId: Long): WbInputEntity? = inputs[profileId]
    override suspend fun upsert(entity: WbInputEntity) { inputs[entity.profileId] = entity }
    override suspend fun deleteByProfileId(profileId: Long) { inputs.remove(profileId) }
}

class FakeTakeoffInputDao : TakeoffInputDao {
    private val inputs = mutableMapOf<Long, TakeoffInputEntity>()
    override suspend fun get(profileId: Long): TakeoffInputEntity? = inputs[profileId]
    override suspend fun upsert(entity: TakeoffInputEntity) { inputs[entity.profileId] = entity }
    override suspend fun deleteByProfileId(profileId: Long) { inputs.remove(profileId) }
}

class FakeLandingInputDao : LandingInputDao {
    private val inputs = mutableMapOf<Long, LandingInputEntity>()
    override suspend fun get(profileId: Long): LandingInputEntity? = inputs[profileId]
    override suspend fun upsert(entity: LandingInputEntity) { inputs[entity.profileId] = entity }
    override suspend fun deleteByProfileId(profileId: Long) { inputs.remove(profileId) }
}

class FakeSleepvluchtInputDao : SleepvluchtInputDao {
    private val inputs = mutableMapOf<Long, SleepvluchtInputEntity>()
    override suspend fun get(profileId: Long): SleepvluchtInputEntity? = inputs[profileId]
    override suspend fun upsert(entity: SleepvluchtInputEntity) { inputs[entity.profileId] = entity }
    override suspend fun deleteByProfileId(profileId: Long) { inputs.remove(profileId) }
}

class FakeAirfieldDao : AirfieldDao {
    private val airfields = MutableStateFlow<List<AirfieldEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<AirfieldEntity>> = airfields
    override suspend fun getById(id: Long): AirfieldEntity? = airfields.value.find { it.id == id }

    override suspend fun insert(airfield: AirfieldEntity): Long {
        val id = nextId++
        airfields.value = airfields.value + airfield.copy(id = id)
        return id
    }

    override suspend fun update(airfield: AirfieldEntity) {
        airfields.value = airfields.value.map { if (it.id == airfield.id) airfield else it }
    }

    override suspend fun delete(airfield: AirfieldEntity) {
        airfields.value = airfields.value.filterNot { it.id == airfield.id }
    }

    fun seed(airfield: AirfieldEntity) {
        airfields.value = airfields.value + airfield
        nextId = maxOf(nextId, airfield.id + 1)
    }
}

class FakeRunwayStripDao : RunwayStripDao {
    private val strips = MutableStateFlow<List<RunwayStripEntity>>(emptyList())
    private var nextId = 1L

    override fun observeByAirfield(airfieldId: Long): Flow<List<RunwayStripEntity>> =
        MutableStateFlow(strips.value.filter { it.airfieldId == airfieldId })

    override suspend fun getByAirfield(airfieldId: Long): List<RunwayStripEntity> =
        strips.value.filter { it.airfieldId == airfieldId }

    override suspend fun insert(strip: RunwayStripEntity): Long {
        val id = nextId++
        strips.value = strips.value + strip.copy(id = id)
        return id
    }

    override suspend fun update(strip: RunwayStripEntity) {
        strips.value = strips.value.map { if (it.id == strip.id) strip else it }
    }

    override suspend fun delete(strip: RunwayStripEntity) {
        strips.value = strips.value.filterNot { it.id == strip.id }
    }

    override suspend fun deleteByAirfieldId(airfieldId: Long) {
        strips.value = strips.value.filterNot { it.airfieldId == airfieldId }
    }

    fun seed(strip: RunwayStripEntity) {
        strips.value = strips.value + strip
        nextId = maxOf(nextId, strip.id + 1)
    }
}

class FakeFlightContextDao : FlightContextDao {
    private val contexts = mutableMapOf<Long, FlightContextEntity>()
    override suspend fun get(profileId: Long): FlightContextEntity? = contexts[profileId]
    override suspend fun upsert(entity: FlightContextEntity) { contexts[entity.profileId] = entity }
    override suspend fun deleteByProfileId(profileId: Long) { contexts.remove(profileId) }
}

class FakeFavoriteAirfieldDao : FavoriteAirfieldDao {
    private val favorites = MutableStateFlow<List<FavoriteAirfieldEntity>>(emptyList())

    override fun observeAll(): Flow<List<FavoriteAirfieldEntity>> = favorites

    override suspend fun insert(entity: FavoriteAirfieldEntity) {
        if (favorites.value.none { it.airfieldId == entity.airfieldId }) favorites.value = favorites.value + entity
    }

    override suspend fun deleteByAirfieldId(airfieldId: Long) {
        favorites.value = favorites.value.filterNot { it.airfieldId == airfieldId }
    }
}

/** Fase 4a — see [RealLifeLogEntity]. */
class FakeRealLifeLogDao : RealLifeLogDao {
    private val logs = MutableStateFlow<List<RealLifeLogEntity>>(emptyList())
    private var nextId = 1L

    override fun observeByProfile(profileId: Long): Flow<List<RealLifeLogEntity>> =
        MutableStateFlow(logs.value.filter { it.profileId == profileId }.sortedByDescending { it.startedAtEpochMs })

    override suspend fun getById(logId: Long): RealLifeLogEntity? = logs.value.find { it.id == logId }

    override suspend fun insert(log: RealLifeLogEntity): Long {
        val id = nextId++
        logs.value = logs.value + log.copy(id = id)
        return id
    }

    override suspend fun markStopped(logId: Long, stoppedAt: Long, reason: String) {
        logs.value = logs.value.map {
            if (it.id == logId) it.copy(stoppedAtEpochMs = stoppedAt, stopReason = reason) else it
        }
    }

    override suspend fun update(log: RealLifeLogEntity) {
        logs.value = logs.value.map { if (it.id == log.id) log else it }
    }

    override suspend fun delete(log: RealLifeLogEntity) {
        logs.value = logs.value.filterNot { it.id == log.id }
    }

    override suspend fun getIdsByProfileId(profileId: Long): List<Long> =
        logs.value.filter { it.profileId == profileId }.map { it.id }

    override suspend fun deleteByProfileId(profileId: Long) {
        logs.value = logs.value.filterNot { it.profileId == profileId }
    }

    fun seed(log: RealLifeLogEntity) {
        logs.value = logs.value + log
        nextId = maxOf(nextId, log.id + 1)
    }
}

class FakeLocationSampleDao : LocationSampleDao {
    private val samples = mutableListOf<LocationSampleEntity>()
    override suspend fun insertAll(samples: List<LocationSampleEntity>) { this.samples += samples }
    override suspend fun getByLog(logId: Long): List<LocationSampleEntity> = samples.filter { it.logId == logId }
    override suspend fun countByLog(logId: Long): Int = samples.count { it.logId == logId }
    override suspend fun deleteByLogId(logId: Long) { samples.removeAll { it.logId == logId } }
}

class FakeImuSampleDao : ImuSampleDao {
    private val samples = mutableListOf<ImuSampleEntity>()
    override suspend fun insertAll(samples: List<ImuSampleEntity>) { this.samples += samples }
    override suspend fun getByLog(logId: Long): List<ImuSampleEntity> = samples.filter { it.logId == logId }
    override suspend fun countByLog(logId: Long): Int = samples.count { it.logId == logId }
    override suspend fun deleteByLogId(logId: Long) { samples.removeAll { it.logId == logId } }
}

class FakeBarometerSampleDao : BarometerSampleDao {
    private val samples = mutableListOf<BarometerSampleEntity>()
    override suspend fun insertAll(samples: List<BarometerSampleEntity>) { this.samples += samples }
    override suspend fun getByLog(logId: Long): List<BarometerSampleEntity> = samples.filter { it.logId == logId }
    override suspend fun countByLog(logId: Long): Int = samples.count { it.logId == logId }
    override suspend fun deleteByLogId(logId: Long) { samples.removeAll { it.logId == logId } }
}

class FakeRealLifeMarkerDao : RealLifeMarkerDao {
    private val markers = mutableListOf<RealLifeMarkerEntity>()
    private var nextId = 1L

    override suspend fun insert(marker: RealLifeMarkerEntity): Long {
        val id = nextId++
        markers += marker.copy(id = id)
        return id
    }

    override suspend fun getByLog(logId: Long): List<RealLifeMarkerEntity> =
        markers.filter { it.logId == logId }.sortedBy { it.epochMs }

    override suspend fun deleteByLogId(logId: Long) { markers.removeAll { it.logId == logId } }
}

class FakeSavedCalculationDao : SavedCalculationDao {
    private val calculations = mutableListOf<SavedCalculationEntity>()
    private var nextId = 1L

    override fun observeByProfileAndType(profileId: Long, type: String): Flow<List<SavedCalculationEntity>> =
        MutableStateFlow(
            calculations.filter { it.profileId == profileId && it.type == type }.sortedByDescending { it.createdAtEpochMs }
        )

    override suspend fun insert(calculation: SavedCalculationEntity): Long {
        val id = nextId++
        calculations += calculation.copy(id = id)
        return id
    }

    override suspend fun delete(calculation: SavedCalculationEntity) { calculations.removeAll { it.id == calculation.id } }

    override suspend fun deleteByProfileId(profileId: Long) { calculations.removeAll { it.profileId == profileId } }
}

/** Builds a real [AircraftProfileRepository] backed entirely by the fakes above. */
fun fakeAircraftProfileRepository(
    profileDao: FakeAircraftProfileDao = FakeAircraftProfileDao(),
    lastWbResultDao: FakeLastWbResultDao = FakeLastWbResultDao(),
    favoriteSailplaneTypeDao: FakeFavoriteSailplaneTypeDao = FakeFavoriteSailplaneTypeDao(),
    wbInputDao: FakeWbInputDao = FakeWbInputDao(),
    takeoffInputDao: FakeTakeoffInputDao = FakeTakeoffInputDao(),
    landingInputDao: FakeLandingInputDao = FakeLandingInputDao(),
    sleepvluchtInputDao: FakeSleepvluchtInputDao = FakeSleepvluchtInputDao(),
    airfieldDao: FakeAirfieldDao = FakeAirfieldDao(),
    runwayStripDao: FakeRunwayStripDao = FakeRunwayStripDao(),
    flightContextDao: FakeFlightContextDao = FakeFlightContextDao(),
    favoriteAirfieldDao: FakeFavoriteAirfieldDao = FakeFavoriteAirfieldDao(),
    realLifeLogDao: FakeRealLifeLogDao = FakeRealLifeLogDao(),
    locationSampleDao: FakeLocationSampleDao = FakeLocationSampleDao(),
    imuSampleDao: FakeImuSampleDao = FakeImuSampleDao(),
    barometerSampleDao: FakeBarometerSampleDao = FakeBarometerSampleDao(),
    realLifeMarkerDao: FakeRealLifeMarkerDao = FakeRealLifeMarkerDao(),
    savedCalculationDao: FakeSavedCalculationDao = FakeSavedCalculationDao(),
    /** Straight pass-through: the fakes are plain in-memory lists, so there is nothing to roll
     * back. Production supplies a real Room transaction (see Hk36Application). */
    transaction: suspend (suspend () -> Unit) -> Unit = { block -> block() }
): AircraftProfileRepository = AircraftProfileRepository(
    profileDao, lastWbResultDao, favoriteSailplaneTypeDao,
    wbInputDao, takeoffInputDao, landingInputDao, sleepvluchtInputDao,
    airfieldDao, runwayStripDao, flightContextDao, favoriteAirfieldDao,
    realLifeLogDao, locationSampleDao, imuSampleDao, barometerSampleDao, realLifeMarkerDao,
    savedCalculationDao, transaction
)
