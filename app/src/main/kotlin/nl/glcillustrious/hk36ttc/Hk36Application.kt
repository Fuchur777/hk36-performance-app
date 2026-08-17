package nl.glcillustrious.hk36ttc

import android.app.Application
import androidx.room.withTransaction
import nl.glcillustrious.hk36ttc.data.catalog.AirportCatalogDatabase
import nl.glcillustrious.hk36ttc.data.catalog.AirportCatalogRepository
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AppDatabase
import nl.glcillustrious.hk36ttc.data.local.CalculationDataStore
import nl.glcillustrious.hk36ttc.data.metar.MetarRepository

class Hk36Application : Application() {

    lateinit var repository: AircraftProfileRepository
        private set

    /** The OurAirports reference catalogue — a separate database from [repository]'s, on
     * purpose; see [AirportCatalogDatabase]. Nothing is loaded here: the catalogue seeds itself
     * on first use so app start stays fast. */
    lateinit var airportCatalogRepository: AirportCatalogRepository
        private set

    /** Online METAR lookup. Purely additive: everything keeps working offline without it. */
    lateinit var metarRepository: MetarRepository
        private set

    lateinit var calculationDataStore: CalculationDataStore
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = AircraftProfileRepository(
            database.aircraftProfileDao(),
            database.lastWbResultDao(),
            database.favoriteSailplaneTypeDao(),
            database.wbInputDao(),
            database.takeoffInputDao(),
            database.landingInputDao(),
            database.sleepvluchtInputDao(),
            database.airfieldDao(),
            database.runwayStripDao(),
            database.flightContextDao(),
            database.favoriteAirfieldDao()
        )
        val catalogDatabase = AirportCatalogDatabase.getInstance(this)
        airportCatalogRepository = AirportCatalogRepository(
            airportDao = catalogDatabase.airportCatalogDao(),
            runwayDao = catalogDatabase.runwayCatalogDao(),
            metaDao = catalogDatabase.catalogMetaDao(),
            userRepository = repository,
            openAsset = { path -> assets.open(path) },
            transaction = { block -> catalogDatabase.withTransaction { block() } }
        )
        metarRepository = MetarRepository(repository)
        calculationDataStore = CalculationDataStore(this)
    }
}
