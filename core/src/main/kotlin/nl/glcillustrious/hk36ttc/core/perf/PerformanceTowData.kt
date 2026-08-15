package nl.glcillustrious.hk36ttc.core.perf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tow-plane (sailplane towing) performance, loaded at runtime from `performance_tow.json`.
 * Field names mirror the JSON exactly. See rekenlogica.md §2.3 for the class-selection rule
 * this data supports.
 */
@Serializable
data class PerformanceTowData(
    @SerialName("limits") val limits: Limits,
    @SerialName("takeoff_classes") val takeoffClasses: TakeoffClasses,
    @SerialName("climb") val climb: Climb
) {
    @Serializable
    data class Limits(
        @SerialName("max_speed_kmh") val maxSpeedKmh: Double,
        @SerialName("min_speed_kmh") val minSpeedKmh: Double,
        @SerialName("min_design_aerotow_speed_sailplane_kmh") val minDesignAerotowSpeedSailplaneKmh: Double,
        @SerialName("max_towed_sailplane_mass_kg") val maxTowedSailplaneMassKg: Double,
        @SerialName("max_towplane_takeoff_mass_solo_kg") val maxTowplaneTakeoffMassSoloKg: Double,
        @SerialName("max_towplane_takeoff_mass_dual_instruction_kg") val maxTowplaneTakeoffMassDualInstructionKg: Double,
        @SerialName("max_towed_sailplane_mass_dual_instruction_kg") val maxTowedSailplaneMassDualInstructionKg: Double,
        @SerialName("demonstrated_crosswind_kmh") val demonstratedCrosswindKmh: Double
    )

    @Serializable
    data class TakeoffClasses(
        @SerialName("classes") val classes: List<TowClass>
    )

    @Serializable
    data class TowClass(
        @SerialName("id") val id: String,
        @SerialName("sailplane_mass_min_kg") val sailplaneMassMinKg: Double = 0.0,
        @SerialName("sailplane_mass_max_kg") val sailplaneMassMaxKg: Double,
        @SerialName("ld_ratio_min") val ldRatioMin: Double,
        @SerialName("towplane_mass_kg") val towplaneMassKg: Double,
        @SerialName("table") val table: List<TowGridPoint>
    )

    @Serializable
    data class TowGridPoint(
        @SerialName("headwind_kts") val headwindKts: Double,
        @SerialName("oat_c") val oatC: Double,
        @SerialName("pressure_alt_m") val pressureAltM: Double,
        @SerialName("s1_m") val s1M: Double,
        @SerialName("s2_m") val s2M: Double
    )

    @Serializable
    data class Climb(
        @SerialName("points") val points: List<ClimbPoint>
    )

    @Serializable
    data class ClimbPoint(
        @SerialName("sailplane_mass_kg") val sailplaneMassKg: Double,
        @SerialName("max_rate_of_climb_ms") val maxRateOfClimbMs: Double
    )

    /** The four solo mass-class tables, in ascending weight order — NOT including
     * `instruction_flight`, which is a separate dual-crew flight mode chosen explicitly by
     * the user, not part of the mass/L-D "bump to next class" ladder. */
    val soloClassesByAscendingMass: List<TowClass>
        get() = takeoffClasses.classes.filter { it.id != "instruction_flight" }
            .sortedBy { it.sailplaneMassMaxKg }

    val instructionClass: TowClass?
        get() = takeoffClasses.classes.firstOrNull { it.id == "instruction_flight" }
}

private val performanceTowJson = Json { ignoreUnknownKeys = true }

fun parsePerformanceTowData(json: String): PerformanceTowData =
    performanceTowJson.decodeFromString(PerformanceTowData.serializer(), json)
