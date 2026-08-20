package nl.schellenberg.hk36ttc.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real, hand-written migrations for every `AppDatabase` version bump so far, replacing
 * `fallbackToDestructiveMigration` (which used to silently wipe every locally stored profile
 * and calculation input on any schema change — see docs/00-plan.md §11 point 2). The exact
 * `CREATE TABLE` SQL for each version is copied from the tracked schema exports in
 * `app/schemas/nl.schellenberg.hk36ttc.data.local.AppDatabase/` (1.json through 7.json) —
 * Room validates the post-migration schema against that export on the next app start, so any
 * drift here fails loudly instead of silently.
 */

/** v1 -> v2: dropped the `serialNumber` column from `aircraft_profiles`. SQLite's
 * `ALTER TABLE ... DROP COLUMN` needs SQLite 3.35+, not guaranteed on minSdk 26, so this uses
 * the standard rebuild-and-copy pattern instead. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `aircraft_profiles_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`registration` TEXT NOT NULL, `emptyMassKg` REAL NOT NULL, `emptyMassCgPositionMm` REAL NOT NULL, " +
                "`mtowKg` REAL NOT NULL, `cgEnvelopeForwardLimitMm` REAL NOT NULL, `cgEnvelopeAftLimitMm` REAL NOT NULL, " +
                "`fuelTankType` TEXT NOT NULL)"
        )
        db.execSQL(
            "INSERT INTO `aircraft_profiles_new` (id, registration, emptyMassKg, emptyMassCgPositionMm, mtowKg, " +
                "cgEnvelopeForwardLimitMm, cgEnvelopeAftLimitMm, fuelTankType) " +
                "SELECT id, registration, emptyMassKg, emptyMassCgPositionMm, mtowKg, cgEnvelopeForwardLimitMm, " +
                "cgEnvelopeAftLimitMm, fuelTankType FROM `aircraft_profiles`"
        )
        db.execSQL("DROP TABLE `aircraft_profiles`")
        db.execSQL("ALTER TABLE `aircraft_profiles_new` RENAME TO `aircraft_profiles`")
    }
}

/** v2 -> v3: added `last_wb_results` (purely additive). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `last_wb_results` (`profileId` INTEGER NOT NULL, " +
                "`totalMassKg` REAL NOT NULL, `computedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`profileId`))"
        )
    }
}

/** v3 -> v4: added `favorite_sailplane_types` (purely additive). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorite_sailplane_types` (`name` TEXT NOT NULL, PRIMARY KEY(`name`))"
        )
    }
}

/** v4 -> v5: added the 4 per-registration form-input tables (purely additive). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wb_inputs` (`profileId` INTEGER NOT NULL, `pilotKg` INTEGER NOT NULL, " +
                "`copilotKg` INTEGER NOT NULL, `fuelLiters` INTEGER NOT NULL, `baggageKg` INTEGER NOT NULL, " +
                "PRIMARY KEY(`profileId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `takeoff_inputs` (`profileId` INTEGER NOT NULL, `oatC` INTEGER NOT NULL, " +
                "`pressureAltM` INTEGER NOT NULL, `headwindKts` INTEGER NOT NULL, `surfaceType` TEXT NOT NULL, " +
                "`slopePct` INTEGER NOT NULL, `marginFactorPct` INTEGER NOT NULL, PRIMARY KEY(`profileId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `landing_inputs` (`profileId` INTEGER NOT NULL, `oatC` INTEGER NOT NULL, " +
                "`pressureAltM` INTEGER NOT NULL, `surfaceType` TEXT NOT NULL, `customSurfaceFactorPct` INTEGER NOT NULL, " +
                "`slopePct` INTEGER NOT NULL, `marginFactorPct` INTEGER NOT NULL, PRIMARY KEY(`profileId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sleepvlucht_inputs` (`profileId` INTEGER NOT NULL, `oatC` INTEGER NOT NULL, " +
                "`pressureAltM` INTEGER NOT NULL, `headwindKts` INTEGER NOT NULL, `slopePct` INTEGER NOT NULL, " +
                "`marginFactorPct` INTEGER NOT NULL, `sailplaneMassKg` INTEGER NOT NULL, `ldRatioKnown` INTEGER NOT NULL, " +
                "`ldRatio` INTEGER NOT NULL, `instructionFlight` INTEGER NOT NULL, `surfaceType` TEXT NOT NULL, " +
                "`selectedSailplaneTypeName` TEXT, `selectedSailplaneTypeUsedFallback` INTEGER NOT NULL, " +
                "`towplaneMassManualOverride` INTEGER NOT NULL, `towplaneMassManualKg` INTEGER NOT NULL, " +
                "PRIMARY KEY(`profileId`))"
        )
    }
}

/** v5 -> v6 (Fase 2c round 1): airfields/runway strips/flight context, plus which runway
 * direction the pilot locked in on each calculation screen. All purely additive — three new
 * tables and one nullable column added to each of the three existing per-screen input tables,
 * so every previously-saved row keeps working unchanged (`chosenRunwayDesignator` reads back
 * as `null`, meaning "follow the live advisor recommendation"). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `airfields` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `icao` TEXT, `metarStationIcao` TEXT, `elevationM` REAL NOT NULL, " +
                "`metarRaw` TEXT, `metarEnteredAtEpochMs` INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `runway_strips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`airfieldId` INTEGER NOT NULL, `designatorA` TEXT NOT NULL, `designatorB` TEXT NOT NULL, " +
                "`headingDegTrueA` REAL NOT NULL, `lengthM` REAL NOT NULL, `surface` TEXT NOT NULL, " +
                "`slopePctA` REAL NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `flight_contexts` (`profileId` INTEGER NOT NULL, `mode` TEXT NOT NULL, " +
                "`airfieldId` INTEGER, `grassCondition` TEXT NOT NULL, PRIMARY KEY(`profileId`))"
        )
        db.execSQL("ALTER TABLE `takeoff_inputs` ADD COLUMN `chosenRunwayDesignator` TEXT")
        db.execSQL("ALTER TABLE `landing_inputs` ADD COLUMN `chosenRunwayDesignator` TEXT")
        db.execSQL("ALTER TABLE `sleepvlucht_inputs` ADD COLUMN `chosenRunwayDesignator` TEXT")
    }
}

/** v6 -> v7 (Fase 2c bugfix/feedback round): one-way runway support (`oneWay`, additive with a
 * SQL-level `DEFAULT 0` — required because SQLite's `ADD COLUMN` rejects a `NOT NULL` column
 * with no default when the table already has rows) and favorite airfields (mirrors
 * `favorite_sailplane_types`). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `runway_strips` ADD COLUMN `oneWay` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorite_airfields` (`airfieldId` INTEGER NOT NULL, PRIMARY KEY(`airfieldId`))"
        )
    }
}

/** v7 -> v8: `airfields.elevationKnown`, so an elevation the catalogue didn't publish stops
 * being indistinguishable from a real 0 m field (several Dutch fields sit at or below sea
 * level). Additive with a SQL-level `DEFAULT 1`: every airfield that already exists was either
 * typed by the pilot or imported with a published elevation, so "known" is the right answer for
 * all of them — there is no way to tell retroactively which imports guessed, which is exactly
 * why the flag has to be stored from now on. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `airfields` ADD COLUMN `elevationKnown` INTEGER NOT NULL DEFAULT 1")
    }
}

/** Every migration `AppDatabase` currently ships, in order. Add the next one here (and never
 * remove an old one) whenever `AppDatabase.version` is bumped again. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
)
