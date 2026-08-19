package nl.glcillustrious.hk36ttc.data.export

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.glcillustrious.hk36ttc.core.wb.FuelTankType
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileEntity
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FavoriteAirfieldEntity
import nl.glcillustrious.hk36ttc.data.local.FavoriteSailplaneTypeEntity
import nl.glcillustrious.hk36ttc.data.local.FlightContextEntity
import nl.glcillustrious.hk36ttc.data.local.LandingInputEntity
import nl.glcillustrious.hk36ttc.data.local.LastWbResultEntity
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity
import nl.glcillustrious.hk36ttc.data.local.SleepvluchtInputEntity
import nl.glcillustrious.hk36ttc.data.local.TakeoffInputEntity
import nl.glcillustrious.hk36ttc.data.local.WbInputEntity

/**
 * Everything the pilot has entered themselves, as one portable JSON file.
 *
 * **A deliberate DTO layer, not the Room entities annotated directly.** A backup is kept for
 * years and re-imported after a reinstall, so its format has to be a decision rather than a
 * side effect: annotating the entities would let any future schema change silently alter the
 * file format. Here, a format change is a visible edit to this file. It also isn't a 1:1 mirror
 * anyway — the language preference lives in SharedPreferences, not Room.
 *
 * **Not included**: the OurAirports catalogue (a separate, deliberately disposable database that
 * rebuilds itself, and 7 MB has no business in a backup) and the JSON calculation data (that is
 * configuration with its own "restore defaults", not user data).
 */
@Serializable
data class UserDataExport(
    @SerialName("schema_version") val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("exported_at_epoch_ms") val exportedAtEpochMs: Long,
    @SerialName("app_version_name") val appVersionName: String,
    /** "nl"/"en", or null when the app follows the device language. */
    @SerialName("language_tag") val languageTag: String? = null,
    @SerialName("aircraft_profiles") val aircraftProfiles: List<AircraftProfileDto> = emptyList(),
    @SerialName("favorite_sailplane_types") val favoriteSailplaneTypes: List<String> = emptyList(),
    @SerialName("airfields") val airfields: List<AirfieldDto> = emptyList(),
    @SerialName("runway_strips") val runwayStrips: List<RunwayStripDto> = emptyList(),
    @SerialName("favorite_airfield_ids") val favoriteAirfieldIds: List<Long> = emptyList(),
    @SerialName("flight_contexts") val flightContexts: List<FlightContextDto> = emptyList(),
    @SerialName("wb_inputs") val wbInputs: List<WbInputDto> = emptyList(),
    @SerialName("takeoff_inputs") val takeoffInputs: List<TakeoffInputDto> = emptyList(),
    @SerialName("landing_inputs") val landingInputs: List<LandingInputDto> = emptyList(),
    @SerialName("sleepvlucht_inputs") val sleepvluchtInputs: List<SleepvluchtInputDto> = emptyList(),
    @SerialName("last_wb_results") val lastWbResults: List<LastWbResultDto> = emptyList()
) {
    /** Counts for the confirmation dialog, so the pilot sees what they are about to swap in. */
    val profileCount: Int get() = aircraftProfiles.size
    val airfieldCount: Int get() = airfields.size

    companion object {
        /**
         * Bump only when the format changes in a way an older app couldn't read. The importer
         * refuses anything newer than it knows rather than half-reading it.
         */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class AircraftProfileDto(
    val id: Long,
    val registration: String,
    @SerialName("empty_mass_kg") val emptyMassKg: Double,
    @SerialName("empty_mass_cg_position_mm") val emptyMassCgPositionMm: Double,
    @SerialName("mtow_kg") val mtowKg: Double,
    @SerialName("cg_envelope_forward_limit_mm") val cgEnvelopeForwardLimitMm: Double,
    @SerialName("cg_envelope_aft_limit_mm") val cgEnvelopeAftLimitMm: Double,
    @SerialName("fuel_tank_type") val fuelTankType: String
)

@Serializable
data class AirfieldDto(
    val id: Long,
    val name: String,
    val icao: String? = null,
    @SerialName("metar_station_icao") val metarStationIcao: String? = null,
    @SerialName("elevation_m") val elevationM: Double,
    /** Defaults to true so a backup written before this field existed restores as "known" —
     * matching MIGRATION_7_8, which makes the same assumption for rows already in the database. */
    @SerialName("elevation_known") val elevationKnown: Boolean = true,
    @SerialName("metar_raw") val metarRaw: String? = null,
    @SerialName("metar_entered_at_epoch_ms") val metarEnteredAtEpochMs: Long? = null
)

@Serializable
data class RunwayStripDto(
    val id: Long,
    @SerialName("airfield_id") val airfieldId: Long,
    @SerialName("designator_a") val designatorA: String,
    @SerialName("designator_b") val designatorB: String,
    @SerialName("heading_deg_true_a") val headingDegTrueA: Double,
    @SerialName("length_m") val lengthM: Double,
    val surface: String,
    @SerialName("slope_pct_a") val slopePctA: Double,
    @SerialName("one_way") val oneWay: Boolean = false
)

@Serializable
data class FlightContextDto(
    @SerialName("profile_id") val profileId: Long,
    val mode: String,
    @SerialName("airfield_id") val airfieldId: Long? = null,
    @SerialName("grass_condition") val grassCondition: String
)

@Serializable
data class WbInputDto(
    @SerialName("profile_id") val profileId: Long,
    @SerialName("pilot_kg") val pilotKg: Int,
    @SerialName("copilot_kg") val copilotKg: Int,
    @SerialName("fuel_liters") val fuelLiters: Int,
    @SerialName("baggage_kg") val baggageKg: Int
)

@Serializable
data class TakeoffInputDto(
    @SerialName("profile_id") val profileId: Long,
    @SerialName("oat_c") val oatC: Int,
    @SerialName("pressure_alt_m") val pressureAltM: Int,
    @SerialName("headwind_kts") val headwindKts: Int,
    @SerialName("surface_type") val surfaceType: String,
    @SerialName("slope_pct") val slopePct: Int,
    @SerialName("margin_factor_pct") val marginFactorPct: Int
)

@Serializable
data class LandingInputDto(
    @SerialName("profile_id") val profileId: Long,
    @SerialName("oat_c") val oatC: Int,
    @SerialName("pressure_alt_m") val pressureAltM: Int,
    @SerialName("surface_type") val surfaceType: String,
    @SerialName("custom_surface_factor_pct") val customSurfaceFactorPct: Int,
    @SerialName("slope_pct") val slopePct: Int,
    @SerialName("margin_factor_pct") val marginFactorPct: Int
)

@Serializable
data class SleepvluchtInputDto(
    @SerialName("profile_id") val profileId: Long,
    @SerialName("oat_c") val oatC: Int,
    @SerialName("pressure_alt_m") val pressureAltM: Int,
    @SerialName("headwind_kts") val headwindKts: Int,
    @SerialName("slope_pct") val slopePct: Int,
    @SerialName("margin_factor_pct") val marginFactorPct: Int,
    @SerialName("sailplane_mass_kg") val sailplaneMassKg: Int,
    @SerialName("ld_ratio_known") val ldRatioKnown: Boolean,
    @SerialName("ld_ratio") val ldRatio: Int,
    @SerialName("instruction_flight") val instructionFlight: Boolean,
    @SerialName("surface_type") val surfaceType: String,
    @SerialName("selected_sailplane_type_name") val selectedSailplaneTypeName: String? = null,
    @SerialName("selected_sailplane_type_used_fallback") val selectedSailplaneTypeUsedFallback: Boolean = false,
    @SerialName("towplane_mass_manual_override") val towplaneMassManualOverride: Boolean = false,
    @SerialName("towplane_mass_manual_kg") val towplaneMassManualKg: Int
)

@Serializable
data class LastWbResultDto(
    @SerialName("profile_id") val profileId: Long,
    @SerialName("total_mass_kg") val totalMassKg: Double,
    @SerialName("computed_at_epoch_ms") val computedAtEpochMs: Long
)

// --- Entity <-> DTO -------------------------------------------------------------------
//
// Ids are carried across verbatim on purpose. Because import replaces everything rather than
// merging, the rows can go back under their original ids, and every cross-reference
// (runway_strips.airfieldId, favorite_airfields.airfieldId, every *_inputs.profileId) stays
// valid without any remapping — Room only auto-generates an id when it is 0.

fun AircraftProfileEntity.toDto() = AircraftProfileDto(
    id, registration, emptyMassKg, emptyMassCgPositionMm, mtowKg,
    cgEnvelopeForwardLimitMm, cgEnvelopeAftLimitMm, fuelTankType.name
)

fun AircraftProfileDto.toEntity() = AircraftProfileEntity(
    id = id,
    registration = registration,
    emptyMassKg = emptyMassKg,
    emptyMassCgPositionMm = emptyMassCgPositionMm,
    mtowKg = mtowKg,
    cgEnvelopeForwardLimitMm = cgEnvelopeForwardLimitMm,
    cgEnvelopeAftLimitMm = cgEnvelopeAftLimitMm,
    // Falls back rather than throwing: a file written by a future version with a tank type this
    // build doesn't know shouldn't abort the whole import.
    fuelTankType = runCatching { FuelTankType.valueOf(fuelTankType) }.getOrDefault(FuelTankType.STANDARD_55L)
)

fun AirfieldEntity.toDto() =
    AirfieldDto(
        id = id, name = name, icao = icao, metarStationIcao = metarStationIcao,
        elevationM = elevationM, elevationKnown = elevationKnown,
        metarRaw = metarRaw, metarEnteredAtEpochMs = metarEnteredAtEpochMs
    )

fun AirfieldDto.toEntity() =
    AirfieldEntity(
        id = id, name = name, icao = icao, metarStationIcao = metarStationIcao,
        elevationM = elevationM, elevationKnown = elevationKnown,
        metarRaw = metarRaw, metarEnteredAtEpochMs = metarEnteredAtEpochMs
    )

fun RunwayStripEntity.toDto() =
    RunwayStripDto(id, airfieldId, designatorA, designatorB, headingDegTrueA, lengthM, surface, slopePctA, oneWay)

fun RunwayStripDto.toEntity() =
    RunwayStripEntity(id, airfieldId, designatorA, designatorB, headingDegTrueA, lengthM, surface, slopePctA, oneWay)

fun FlightContextEntity.toDto() = FlightContextDto(profileId, mode, airfieldId, grassCondition)
fun FlightContextDto.toEntity() = FlightContextEntity(profileId, mode, airfieldId, grassCondition)

fun WbInputEntity.toDto() = WbInputDto(profileId, pilotKg, copilotKg, fuelLiters, baggageKg)
fun WbInputDto.toEntity() = WbInputEntity(profileId, pilotKg, copilotKg, fuelLiters, baggageKg)

fun TakeoffInputEntity.toDto() =
    TakeoffInputDto(profileId, oatC, pressureAltM, headwindKts, surfaceType, slopePct, marginFactorPct)

fun TakeoffInputDto.toEntity() = TakeoffInputEntity(
    profileId, oatC, pressureAltM, headwindKts, surfaceType, slopePct, marginFactorPct,
    // Always null in practice since Fase 2c ronde 3 dropped the fixed runway choice; kept out of
    // the file format rather than exporting a column that is dead weight.
    chosenRunwayDesignator = null
)

fun LandingInputEntity.toDto() = LandingInputDto(
    profileId, oatC, pressureAltM, surfaceType, customSurfaceFactorPct, slopePct, marginFactorPct
)

fun LandingInputDto.toEntity() = LandingInputEntity(
    profileId, oatC, pressureAltM, surfaceType, customSurfaceFactorPct, slopePct, marginFactorPct,
    chosenRunwayDesignator = null
)

fun SleepvluchtInputEntity.toDto() = SleepvluchtInputDto(
    profileId, oatC, pressureAltM, headwindKts, slopePct, marginFactorPct, sailplaneMassKg,
    ldRatioKnown, ldRatio, instructionFlight, surfaceType, selectedSailplaneTypeName,
    selectedSailplaneTypeUsedFallback, towplaneMassManualOverride, towplaneMassManualKg
)

fun SleepvluchtInputDto.toEntity() = SleepvluchtInputEntity(
    profileId, oatC, pressureAltM, headwindKts, slopePct, marginFactorPct, sailplaneMassKg,
    ldRatioKnown, ldRatio, instructionFlight, surfaceType, selectedSailplaneTypeName,
    selectedSailplaneTypeUsedFallback, towplaneMassManualOverride, towplaneMassManualKg,
    chosenRunwayDesignator = null
)

fun LastWbResultEntity.toDto() = LastWbResultDto(profileId, totalMassKg, computedAtEpochMs)
fun LastWbResultDto.toEntity() = LastWbResultEntity(profileId, totalMassKg, computedAtEpochMs)

fun FavoriteSailplaneTypeEntity.toName() = name
fun FavoriteAirfieldEntity.toId() = airfieldId

/**
 * Pretty-printed on purpose: a backup a pilot can open in a text editor and sanity-check is
 * worth more than a few saved bytes. [ignoreUnknownKeys] lets a file from a newer build that
 * added a field still import, as long as its schema version is one this build accepts.
 */
val userDataJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}
