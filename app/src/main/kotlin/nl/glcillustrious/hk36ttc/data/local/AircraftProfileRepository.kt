package nl.glcillustrious.hk36ttc.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.glcillustrious.hk36ttc.core.wb.AircraftProfile

class AircraftProfileRepository(
    private val dao: AircraftProfileDao,
    private val lastWbResultDao: LastWbResultDao,
    private val favoriteSailplaneTypeDao: FavoriteSailplaneTypeDao
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
}
