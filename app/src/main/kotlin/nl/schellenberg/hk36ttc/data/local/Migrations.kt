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

/** v8 -> v9 (Fase 4a): `real_life_logs` plus its three raw-sample tables (location/IMU/barometer),
 * all purely additive. `logId` is indexed on every sample table — unlike most owner-id columns
 * elsewhere in this schema, a single recording can produce tens of thousands of sample rows, so
 * an unindexed `WHERE logId = ...` would be a full scan. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `real_life_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`profileId` INTEGER NOT NULL, `configuration` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "`startedAtEpochMs` INTEGER NOT NULL, `stoppedAtEpochMs` INTEGER, `stopReason` TEXT, " +
                "`barometerAvailable` INTEGER NOT NULL, `gpsRequestedIntervalMs` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `location_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`logId` INTEGER NOT NULL, `epochMs` INTEGER NOT NULL, `elapsedRealtimeNanos` INTEGER NOT NULL, " +
                "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `altitudeM` REAL, `speedMps` REAL, " +
                "`speedAccuracyMps` REAL, `bearingDeg` REAL, `bearingAccuracyDeg` REAL, " +
                "`horizontalAccuracyM` REAL, `verticalAccuracyM` REAL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_samples_logId` ON `location_samples` (`logId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `imu_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`logId` INTEGER NOT NULL, `epochMs` INTEGER NOT NULL, `elapsedRealtimeNanos` INTEGER NOT NULL, " +
                "`sensorType` TEXT NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL, `z` REAL NOT NULL, " +
                "`accuracy` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_imu_samples_logId` ON `imu_samples` (`logId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `barometer_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`logId` INTEGER NOT NULL, `epochMs` INTEGER NOT NULL, `elapsedRealtimeNanos` INTEGER NOT NULL, " +
                "`pressureHpa` REAL NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_barometer_samples_logId` ON `barometer_samples` (`logId`)")
    }
}

/** v9 -> v10 (Fase 4a follow-up): `real_life_markers`, so a co-pilot/observer can tap a button
 * during an active recording to mark roll-start/lift-off/15m as an independent ground truth for
 * calibrating Fase 4b's detection against — see [RealLifeMarkerEntity]. Purely additive, no
 * index (unlike the sample tables): a log gets at most a handful of marker rows. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `real_life_markers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`logId` INTEGER NOT NULL, `epochMs` INTEGER NOT NULL, `elapsedRealtimeNanos` INTEGER NOT NULL, " +
                "`markerType` TEXT NOT NULL)"
        )
    }
}

/** v10 -> v11 (Fase 4c): the weather/AFM-comparison snapshot fields on `real_life_logs` — see
 * [RealLifeLogEntity]'s doc comment. Purely additive, every column nullable: all 9 recordings
 * that exist today read back with every one of these as `null` until backfilled via the detail
 * screen's "Condities bewerken" flow. No index needed -- these are only ever read one row at a
 * time (the recording currently open), never filtered/searched across recordings. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `airfieldId` INTEGER")
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `surfaceType` TEXT")
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `slopePct` REAL")
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `oatC` INTEGER")
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `pressureAltM` INTEGER")
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `windDirectionDeg` INTEGER")
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `windSpeedKts` INTEGER")
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `metarRaw` TEXT")
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `metarObservedAtEpochMs` INTEGER")
        db.execSQL("ALTER TABLE `real_life_logs` ADD COLUMN `conditionsSource` TEXT")
    }
}

/** Every migration `AppDatabase` currently ships, in order. Add the next one here (and never
 * remove an old one) whenever `AppDatabase.version` is bumped again. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
)
