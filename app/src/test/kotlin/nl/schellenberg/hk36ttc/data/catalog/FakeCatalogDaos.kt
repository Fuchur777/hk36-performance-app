package nl.schellenberg.hk36ttc.data.catalog

/**
 * In-memory stand-ins for the catalogue DAOs, matching the hand-written fakes in
 * `data/local/FakeDaos.kt`. [FakeAirportCatalogDao.search] only has to be good enough to prove
 * the copy-out rules; the real ranking is SQL and is verified on-device.
 */
class FakeAirportCatalogDao : AirportCatalogDao {
    private val rows = mutableListOf<AirportCatalogEntity>()
    private var nextId = 1L

    override suspend fun search(exact: String, prefix: String, contains: String, limit: Int): List<AirportCatalogEntity> {
        val needle = contains.trim('%').lowercase()
        return rows.filter {
            it.icaoCode.equals(exact, ignoreCase = true) ||
                it.ident.equals(exact, ignoreCase = true) ||
                it.name.lowercase().contains(needle)
        }.take(limit)
    }

    /** Mirrors the real query's column set *and* its preference order — an ICAO match first,
     * then ident, then the GPS/local codes. */
    override suspend fun findByCode(code: String): AirportCatalogEntity? =
        rows.filter {
            it.icaoCode.equals(code, ignoreCase = true) ||
                it.ident.equals(code, ignoreCase = true) ||
                it.gpsCode.equals(code, ignoreCase = true) ||
                it.localCode.equals(code, ignoreCase = true)
        }.minByOrNull {
            when {
                it.icaoCode.equals(code, ignoreCase = true) -> 0
                it.ident.equals(code, ignoreCase = true) -> 1
                else -> 2
            }
        }

    override suspend fun count(): Int = rows.size

    override suspend fun insertAll(airports: List<AirportCatalogEntity>) {
        airports.forEach { rows += it.copy(id = nextId++) }
    }

    override suspend fun clear() {
        rows.clear()
    }

    fun seed(airport: AirportCatalogEntity) {
        rows += airport.copy(id = nextId++)
    }
}

class FakeRunwayCatalogDao : RunwayCatalogDao {
    private val rows = mutableListOf<RunwayCatalogEntity>()
    private var nextId = 1L

    override suspend fun forAirport(airportIdent: String): List<RunwayCatalogEntity> =
        rows.filter { it.airportIdent == airportIdent }.sortedByDescending { it.lengthM }

    override suspend fun count(): Int = rows.size

    override suspend fun insertAll(runways: List<RunwayCatalogEntity>) {
        runways.forEach { rows += it.copy(id = nextId++) }
    }

    override suspend fun clear() {
        rows.clear()
    }

    fun seed(runway: RunwayCatalogEntity) {
        rows += runway.copy(id = nextId++)
    }
}

class FakeCatalogMetaDao : CatalogMetaDao {
    private var meta: CatalogMetaEntity? = null
    override suspend fun get(id: Int): CatalogMetaEntity? = meta
    override suspend fun upsert(meta: CatalogMetaEntity) { this.meta = meta }
}
