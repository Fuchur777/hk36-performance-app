package nl.glcillustrious.hk36ttc.data.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.glcillustrious.hk36ttc.core.units.AppUnits
import nl.glcillustrious.hk36ttc.core.wb.FuelTankType
import nl.glcillustrious.hk36ttc.data.local.FavoriteAirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FavoriteSailplaneTypeEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextMode
import nl.glcillustrious.hk36ttc.data.local.GrassCondition
import nl.glcillustrious.hk36ttc.data.local.RunwaySurfaceType
import nl.glcillustrious.hk36ttc.data.local.UserDataDao
import nl.glcillustrious.hk36ttc.ui.perf.LandingSurfaceType
import nl.glcillustrious.hk36ttc.ui.perf.SleepvluchtSurfaceType
import nl.glcillustrious.hk36ttc.ui.perf.TakeoffSurfaceType

/** What reading a backup file produced, before anything is written to the database. */
sealed interface ImportParseResult {
    data class Ok(val data: UserDataExport) : ImportParseResult

    /** Written by a newer app version whose format this build doesn't know. */
    data class TooNew(val fileVersion: Int, val supportedVersion: Int) : ImportParseResult

    /** Not readable as a backup at all — wrong file picked, or damaged. */
    data class Unreadable(val reason: String) : ImportParseResult
}

/**
 * Builds and restores the portable backup of everything the pilot entered themselves.
 *
 * **Import replaces rather than merges**, by Frank's choice, and that is what keeps this simple
 * and safe: every table is wiped and refilled with the file's own rows *under their original
 * ids*, so cross-references (a runway strip to its airfield, a saved input to its registration)
 * remain valid without any id remapping — the part of a merge strategy that would be most
 * likely to go quietly wrong.
 *
 * The whole replace runs in one transaction supplied by the caller, so a failure halfway leaves
 * the existing data untouched rather than half-erased.
 */
class UserDataRepository(
    private val dao: UserDataDao,
    /** Reads/writes the language override, which lives in SharedPreferences rather than Room. */
    private val languageTag: () -> String?,
    private val setLanguageTag: (String?) -> Unit,
    /** Reads/writes the pilot's chosen display units, which also live in SharedPreferences
     * rather than Room — see [nl.glcillustrious.hk36ttc.data.local.UnitPreferences]. */
    private val units: () -> AppUnits,
    private val setUnits: (AppUnits) -> Unit,
    /** Runs [block] in one database transaction. Injected so this class stays JVM-testable. */
    private val transaction: suspend (suspend () -> Unit) -> Unit,
    private val appVersionName: String,
    private val now: () -> Long = System::currentTimeMillis
) {

    suspend fun buildExport(): UserDataExport = withContext(Dispatchers.IO) {
        UserDataExport(
            exportedAtEpochMs = now(),
            appVersionName = appVersionName,
            languageTag = languageTag(),
            units = units().toDto(),
            aircraftProfiles = dao.allProfiles().map { it.toDto() },
            favoriteSailplaneTypes = dao.allFavoriteSailplaneTypes().map { it.toName() },
            airfields = dao.allAirfields().map { it.toDto() },
            runwayStrips = dao.allRunwayStrips().map { it.toDto() },
            favoriteAirfieldIds = dao.allFavoriteAirfields().map { it.toId() },
            flightContexts = dao.allFlightContexts().map { it.toDto() },
            wbInputs = dao.allWbInputs().map { it.toDto() },
            takeoffInputs = dao.allTakeoffInputs().map { it.toDto() },
            landingInputs = dao.allLandingInputs().map { it.toDto() },
            sleepvluchtInputs = dao.allSleepvluchtInputs().map { it.toDto() },
            lastWbResults = dao.allLastWbResults().map { it.toDto() }
        )
    }

    fun serialize(export: UserDataExport): String = userDataJson.encodeToString(export)

    /**
     * Reads and validates a backup **without touching the database** — the confirmation dialog
     * needs to show what is in the file before the pilot agrees to lose what they have.
     *
     * Validation covers more than the JSON shape. Several columns hold an enum's `name`, and the
     * app reads them back with `valueOf`, which throws on anything unexpected. Because
     * [replaceAll] wipes every table before it inserts, a bad value found *after* the import has
     * started would cost the pilot their data and leave a screen that crashes on open. So every
     * enum-valued string is checked here, while refusing the file still costs nothing.
     */
    fun parse(json: String): ImportParseResult = try {
        val data = userDataJson.decodeFromString<UserDataExport>(json)
        val badValue = data.firstUnknownEnumValue()
        when {
            data.schemaVersion > UserDataExport.CURRENT_SCHEMA_VERSION ->
                ImportParseResult.TooNew(data.schemaVersion, UserDataExport.CURRENT_SCHEMA_VERSION)
            badValue != null -> ImportParseResult.Unreadable(badValue)
            else -> ImportParseResult.Ok(data)
        }
    } catch (e: Exception) {
        // Deliberately broad: kotlinx-serialization throws several unrelated types for a
        // malformed file, and every one of them means the same thing to the pilot.
        ImportParseResult.Unreadable(e.message ?: "onbekende fout")
    }

    /**
     * The first enum-valued string in [this] that no enum in this build knows, described well
     * enough to put in front of the pilot — or null when every one of them is recognised.
     *
     * Ordered the same way [replaceAll] inserts, so the message names whichever table the pilot
     * would hit first.
     */
    private fun UserDataExport.firstUnknownEnumValue(): String? {
        fun check(field: String, value: String, allowed: Array<out Enum<*>>): String? =
            if (allowed.none { it.name == value }) "$field: \"$value\"" else null

        return aircraftProfiles.firstNotNullOfOrNull { check("fuel_tank_type", it.fuelTankType, FuelTankType.values()) }
            ?: runwayStrips.firstNotNullOfOrNull { check("surface", it.surface, RunwaySurfaceType.values()) }
            ?: flightContexts.firstNotNullOfOrNull { check("mode", it.mode, FlightContextMode.values()) }
            ?: flightContexts.firstNotNullOfOrNull {
                check("grass_condition", it.grassCondition, GrassCondition.values())
            }
            ?: takeoffInputs.firstNotNullOfOrNull {
                check("takeoff surface_type", it.surfaceType, TakeoffSurfaceType.values())
            }
            ?: landingInputs.firstNotNullOfOrNull {
                check("landing surface_type", it.surfaceType, LandingSurfaceType.values())
            }
            ?: sleepvluchtInputs.firstNotNullOfOrNull {
                check("sleepvlucht surface_type", it.surfaceType, SleepvluchtSurfaceType.values())
            }
    }

    /**
     * Replaces all user data with [data]. Destructive by design — the caller must have
     * confirmed with the pilot first.
     *
     * Order matters only in that everything is cleared before anything is inserted; within the
     * transaction there are no foreign keys to satisfy (the schema has none), so the ids from
     * the file are simply restored as-is.
     */
    suspend fun replaceAll(data: UserDataExport) = withContext(Dispatchers.IO) {
        transaction {
            dao.clearProfiles()
            dao.clearFavoriteSailplaneTypes()
            dao.clearAirfields()
            dao.clearRunwayStrips()
            dao.clearFavoriteAirfields()
            dao.clearFlightContexts()
            dao.clearWbInputs()
            dao.clearTakeoffInputs()
            dao.clearLandingInputs()
            dao.clearSleepvluchtInputs()
            dao.clearLastWbResults()

            dao.insertProfiles(data.aircraftProfiles.map { it.toEntity() })
            dao.insertFavoriteSailplaneTypes(data.favoriteSailplaneTypes.map { FavoriteSailplaneTypeEntity(it) })
            dao.insertAirfields(data.airfields.map { it.toEntity() })
            dao.insertRunwayStrips(data.runwayStrips.map { it.toEntity() })
            dao.insertFavoriteAirfields(data.favoriteAirfieldIds.map { FavoriteAirfieldEntity(it) })
            dao.insertFlightContexts(data.flightContexts.map { it.toEntity() })
            dao.insertWbInputs(data.wbInputs.map { it.toEntity() })
            dao.insertTakeoffInputs(data.takeoffInputs.map { it.toEntity() })
            dao.insertLandingInputs(data.landingInputs.map { it.toEntity() })
            dao.insertSleepvluchtInputs(data.sleepvluchtInputs.map { it.toEntity() })
            dao.insertLastWbResults(data.lastWbResults.map { it.toEntity() })
        }
        // Outside the transaction: this is SharedPreferences, not Room, and it must only change
        // once the data it belongs with is actually in place.
        setLanguageTag(data.languageTag)
        setUnits(data.units.toAppUnits())
    }

    /** True when the import would switch the app's language, so the caller knows to recreate
     * the activity — the same thing the language setting itself does. */
    fun languageWouldChange(data: UserDataExport): Boolean = data.languageTag != languageTag()
}
