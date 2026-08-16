package nl.glcillustrious.hk36ttc.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test (needs an emulator/device — `./gradlew :app:connectedAndroidTest`) proving
 * the real [Migration]s in Migrations.kt actually preserve data across every version bump,
 * instead of trusting [fallbackToDestructiveMigration] to silently wipe it. Historic schemas
 * are loaded from the tracked exports under `app/schemas/` (see the `androidTest` source set
 * config in `app/build.gradle.kts`), so this also catches any drift between a
 * hand-written migration and what Room actually expects for a given version.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_dropsSerialNumberButKeepsEveryOtherColumn() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                "INSERT INTO aircraft_profiles (id, registration, serialNumber, emptyMassKg, " +
                    "emptyMassCgPositionMm, mtowKg, cgEnvelopeForwardLimitMm, cgEnvelopeAftLimitMm, fuelTankType) " +
                    "VALUES (1, 'PH-XYZ', 'SN-001', 560.0, 2350.0, 770.0, 2300.0, 2450.0, 'STANDARD_55L')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        val cursor = db.query("SELECT registration, emptyMassKg, mtowKg, fuelTankType FROM aircraft_profiles WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("PH-XYZ", cursor.getString(0))
        assertEquals(560.0, cursor.getDouble(1), 0.0001)
        assertEquals(770.0, cursor.getDouble(2), 0.0001)
        assertEquals("STANDARD_55L", cursor.getString(3))
        cursor.close()
    }

    @Test
    fun migrate2To3_addsLastWbResultsWithoutTouchingAircraftProfiles() {
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                "INSERT INTO aircraft_profiles (id, registration, emptyMassKg, emptyMassCgPositionMm, " +
                    "mtowKg, cgEnvelopeForwardLimitMm, cgEnvelopeAftLimitMm, fuelTankType) " +
                    "VALUES (1, 'PH-XYZ', 560.0, 2350.0, 770.0, 2300.0, 2450.0, 'STANDARD_55L')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 3, true, MIGRATION_2_3)

        val profileCursor = db.query("SELECT registration FROM aircraft_profiles WHERE id = 1")
        assertTrue(profileCursor.moveToFirst())
        assertEquals("PH-XYZ", profileCursor.getString(0))
        profileCursor.close()

        val countCursor = db.query("SELECT COUNT(*) FROM last_wb_results")
        assertTrue(countCursor.moveToFirst())
        assertEquals(0, countCursor.getInt(0))
        countCursor.close()
    }

    @Test
    fun migrateAllTheWayFrom1To5_succeedsAndKeepsSeededProfile() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                "INSERT INTO aircraft_profiles (id, registration, serialNumber, emptyMassKg, " +
                    "emptyMassCgPositionMm, mtowKg, cgEnvelopeForwardLimitMm, cgEnvelopeAftLimitMm, fuelTankType) " +
                    "VALUES (1, 'PH-XYZ', 'SN-001', 560.0, 2350.0, 770.0, 2300.0, 2450.0, 'STANDARD_55L')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 5, true, *ALL_MIGRATIONS)

        val cursor = db.query("SELECT registration FROM aircraft_profiles WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("PH-XYZ", cursor.getString(0))
        cursor.close()
    }
}
