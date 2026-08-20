package nl.schellenberg.hk36ttc.core.perf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Take-off/landing/climb performance for the normal (non-tow) configuration, loaded at
 * runtime from `performance_normal.json` — same "never hardcode calculation values in
 * Kotlin" rule as [nl.schellenberg.hk36ttc.core.wb.WbConstantsData]. Field names mirror
 * the JSON exactly so the file stays hand-editable.
 *
 * Baseline figures published alongside the AFM table (e.g. "182 m ground roll at MSL/ISA")
 * are deliberately NOT modeled separately here — they are exactly the table row at
 * (headwind=0, oat=15, alt=0), so re-reading them from [takeoff]/[landing] avoids a second
 * source of truth that could silently drift from the table.
 */
@Serializable
data class PerformanceNormalData(
    @SerialName("takeoff") val takeoff: Takeoff,
    @SerialName("landing") val landing: Landing,
    @SerialName("climb") val climb: Climb,
    @SerialName("demonstrated_crosswind_kmh") val demonstratedCrosswindKmh: Double
) {
    @Serializable
    data class Takeoff(
        @SerialName("grass_runway_penalty_min_pct") val grassRunwayPenaltyMinPct: Double,
        @SerialName("table") val table: List<TakeoffGridPoint>
    )

    @Serializable
    data class TakeoffGridPoint(
        @SerialName("headwind_kts") val headwindKts: Double,
        @SerialName("oat_c") val oatC: Double,
        @SerialName("pressure_alt_m") val pressureAltM: Double,
        @SerialName("s1_m") val s1M: Double,
        @SerialName("s2_m") val s2M: Double
    )

    @Serializable
    data class Landing(
        @SerialName("table") val table: List<LandingGridPoint>
    )

    @Serializable
    data class LandingGridPoint(
        @SerialName("oat_c") val oatC: Double,
        @SerialName("pressure_alt_m") val pressureAltM: Double,
        @SerialName("l1_m") val l1M: Double,
        @SerialName("l2_m") val l2M: Double
    )

    @Serializable
    data class Climb(
        @SerialName("vy_kmh") val vyKmh: Double,
        @SerialName("max_rate_of_climb_ms") val maxRateOfClimbMs: Double,
        @SerialName("service_ceiling_m") val serviceCeilingM: Double
    )
}

private val performanceNormalJson = Json { ignoreUnknownKeys = true }

fun parsePerformanceNormalData(json: String): PerformanceNormalData =
    performanceNormalJson.decodeFromString(PerformanceNormalData.serializer(), json)
