package nl.glcillustrious.hk36ttc.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AircraftProfileEntity::class, LastWbResultEntity::class, FavoriteSailplaneTypeEntity::class,
        WbInputEntity::class, TakeoffInputEntity::class, LandingInputEntity::class, SleepvluchtInputEntity::class,
        AirfieldEntity::class, RunwayStripEntity::class, FlightContextEntity::class, FavoriteAirfieldEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun aircraftProfileDao(): AircraftProfileDao
    abstract fun lastWbResultDao(): LastWbResultDao
    abstract fun favoriteSailplaneTypeDao(): FavoriteSailplaneTypeDao
    abstract fun wbInputDao(): WbInputDao
    abstract fun takeoffInputDao(): TakeoffInputDao
    abstract fun landingInputDao(): LandingInputDao
    abstract fun sleepvluchtInputDao(): SleepvluchtInputDao
    abstract fun airfieldDao(): AirfieldDao
    abstract fun runwayStripDao(): RunwayStripDao
    abstract fun flightContextDao(): FlightContextDao
    abstract fun favoriteAirfieldDao(): FavoriteAirfieldDao

    /** Bulk access across all user tables, for export/import only — see [UserDataDao]. Adds no
     * entity and no schema change, so [version] stays where it is. */
    abstract fun userDataDao(): UserDataDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hk36ttc.db"
                )
                    // Real migrations for every version bump so far — see Migrations.kt.
                    // No fallbackToDestructiveMigration: a schema bump must never silently wipe
                    // a real user's profiles/inputs. Add the next Migration to ALL_MIGRATIONS
                    // whenever AppDatabase.version is bumped again.
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}
