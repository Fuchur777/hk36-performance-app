package nl.glcillustrious.hk36ttc.data.catalog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The OurAirports reference catalogue, in its own database file — deliberately **not** a set of
 * extra tables inside `hk36ttc.db`.
 *
 * Three reasons, in order of importance:
 *
 * 1. **It makes the safety rule structural instead of careful.** The pilot's own airfields and
 *    runways live in `AppDatabase`; nothing in the import or refresh path here has a handle on
 *    them. "Imported data can never overwrite your runways" stops being a property of the code
 *    being right and becomes a property of the code being unable to do otherwise.
 * 2. **It keeps a rebuildable cache out of Android's backup.** The app has
 *    `allowBackup="true"`, and ~120,000 rows of reference data have no business travelling in a
 *    user backup alongside a handful of aircraft profiles.
 * 3. **It makes refreshing trivial.** Replacing the catalogue is `clear()` + `insertAll()` in
 *    one transaction, with nothing else in the file to endanger.
 *
 * Consequently `fallbackToDestructiveMigration` is the *correct* choice here, where it would be
 * indefensible in `AppDatabase`: every row is reconstructible from the bundled asset, so a
 * schema change should just rebuild rather than carry migration code forever.
 */
@Database(
    entities = [AirportCatalogEntity::class, RunwayCatalogEntity::class, CatalogMetaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AirportCatalogDatabase : RoomDatabase() {

    abstract fun airportCatalogDao(): AirportCatalogDao
    abstract fun runwayCatalogDao(): RunwayCatalogDao
    abstract fun catalogMetaDao(): CatalogMetaDao

    companion object {
        @Volatile
        private var instance: AirportCatalogDatabase? = null

        fun getInstance(context: Context): AirportCatalogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AirportCatalogDatabase::class.java,
                    "airport_catalog.db"
                )
                    // Safe here, and only here: this database holds nothing the user typed.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
