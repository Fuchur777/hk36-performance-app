package nl.glcillustrious.hk36ttc.core.metar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Runtime-loaded METAR-related settings (rekenlogica.md §9) — never hardcoded, same pattern
 * as every other calculation JSON in this app. Also the future home for online-lookup API
 * settings once that round is built. */
@Serializable
data class MetarConfigData(
    @SerialName("stale_after_minutes") val staleAfterMinutes: Int
) {
    companion object {
        /** Fallback only for tests/first-run seeding — never the runtime path (see
         * CalculationDataStore). 60 minutes matches rekenlogica.md §9's suggested threshold. */
        val DEFAULT = MetarConfigData(staleAfterMinutes = 60)
    }
}

private val metarConfigJson = Json { ignoreUnknownKeys = true }

fun parseMetarConfigData(json: String): MetarConfigData =
    metarConfigJson.decodeFromString(MetarConfigData.serializer(), json)
