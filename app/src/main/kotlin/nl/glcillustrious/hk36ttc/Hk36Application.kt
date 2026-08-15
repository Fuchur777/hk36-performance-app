package nl.glcillustrious.hk36ttc

import android.app.Application
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AppDatabase
import nl.glcillustrious.hk36ttc.data.local.CalculationDataStore

class Hk36Application : Application() {

    lateinit var repository: AircraftProfileRepository
        private set

    lateinit var calculationDataStore: CalculationDataStore
        private set

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        repository = AircraftProfileRepository(
            database.aircraftProfileDao(),
            database.lastWbResultDao(),
            database.favoriteSailplaneTypeDao()
        )
        calculationDataStore = CalculationDataStore(this)
    }
}
