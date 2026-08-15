package nl.glcillustrious.hk36ttc.core.perf

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reference list of common sailplane types (leeggewicht/MTOW/L-D), loaded at runtime from
 * `sailplane_types.json`, used only to auto-fill the tow-weight fields in Sleepvlucht — see
 * rekenlogica.md §2.3b. Source: XCSoar's open-source polar database (GPL-2.0-or-later); L/D is
 * calculated from XCSoar's own polar points, not an AFM figure. Which types the user has
 * favorited lives in the Room database, not here, so a JSON reset never touches that choice.
 */
@Serializable
data class SailplaneTypesData(
    @SerialName("types") val types: List<SailplaneType>
) {
    @Serializable
    data class SailplaneType(
        @SerialName("name") val name: String,
        @SerialName("empty_mass_kg") val emptyMassKg: Double,
        @SerialName("mtow_kg") val mtowKg: Double,
        @SerialName("ld_ratio") val ldRatio: Double
    )
}

private val sailplaneTypesJson = Json { ignoreUnknownKeys = true }

fun parseSailplaneTypesData(json: String): SailplaneTypesData =
    sailplaneTypesJson.decodeFromString(SailplaneTypesData.serializer(), json)
