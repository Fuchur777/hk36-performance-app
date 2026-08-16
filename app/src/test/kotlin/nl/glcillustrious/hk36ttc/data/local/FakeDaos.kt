package nl.glcillustrious.hk36ttc.data.local

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

/** Builds a real [AircraftProfileRepository] backed entirely by the fakes above. */
fun fakeAircraftProfileRepository(
    profileDao: FakeAircraftProfileDao = FakeAircraftProfileDao(),
    lastWbResultDao: FakeLastWbResultDao = FakeLastWbResultDao(),
    favoriteSailplaneTypeDao: FakeFavoriteSailplaneTypeDao = FakeFavoriteSailplaneTypeDao(),
    wbInputDao: FakeWbInputDao = FakeWbInputDao(),
    takeoffInputDao: FakeTakeoffInputDao = FakeTakeoffInputDao(),
    landingInputDao: FakeLandingInputDao = FakeLandingInputDao(),
    sleepvluchtInputDao: FakeSleepvluchtInputDao = FakeSleepvluchtInputDao()
): AircraftProfileRepository = AircraftProfileRepository(
    profileDao, lastWbResultDao, favoriteSailplaneTypeDao,
    wbInputDao, takeoffInputDao, landingInputDao, sleepvluchtInputDao
)
