package nl.glcillustrious.hk36ttc.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AircraftProfileEntity::class, LastWbResultEntity::class, FavoriteSailplaneTypeEntity::class],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun aircraftProfileDao(): AircraftProfileDao
    abstract fun lastWbResultDao(): LastWbResultDao
    abstract fun favoriteSailplaneTypeDao(): FavoriteSailplaneTypeDao

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
                    // v1 -> v2 dropped serialNumber, v2 -> v3 added last_wb_results,
                    // v3 -> v4 added favorite_sailplane_types.
                    // Pre-release app, no real user data to preserve yet — revisit with a
                    // real Migration once the app ships.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
