package nl.schellenberg.hk36ttc.core.metar

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Runtime-loaded METAR-related settings (rekenlogica.md §9) — never hardcoded, same pattern
 * as every other calculation JSON in this app, including the online-lookup endpoint. */
@Serializable
data class MetarConfigData(
    @SerialName("stale_after_minutes") val staleAfterMinutes: Int,
    /**
     * Endpoint template for the online lookup, with `{stations}` standing in for a
     * comma-separated list of ICAO codes. In the JSON layer rather than in Kotlin so the club
     * can point the app at a different service — or switch it off by blanking it — without a
     * rebuild, exactly as docs/00-plan.md requires of every source setting.
     */
    @SerialName("fetch_url_template") val fetchUrlTemplate: String = DEFAULT_FETCH_URL,
    /**
     * How old a stored METAR may be before the app offers to fetch a fresh one by itself.
     * Separate from [staleAfterMinutes], which only governs the warning: refetching sooner than
     * you warn is reasonable, and an observation is typically reissued every 30 minutes.
     */
    @SerialName("auto_refresh_after_minutes") val autoRefreshAfterMinutes: Int = 20
) {
    /** The URL to call for [stations], or null when the lookup has been switched off. */
    fun fetchUrlFor(stations: List<String>): String? {
        if (fetchUrlTemplate.isBlank() || stations.isEmpty()) return null
        return fetchUrlTemplate.replace("{stations}", stations.joinToString(","))
    }

    companion object {
        /**
         * aviationweather.gov — free, no account and no API key, named as the intended source in
         * docs/00-plan.md Fase 2c. `format=raw` yields one plain METAR per line.
         */
        const val DEFAULT_FETCH_URL = "https://aviationweather.gov/api/data/metar?ids={stations}&format=raw"

        /** Fallback only for tests/first-run seeding — never the runtime path (see
         * CalculationDataStore). 60 minutes matches rekenlogica.md §9's suggested threshold. */
        val DEFAULT = MetarConfigData(staleAfterMinutes = 60)
    }
}

private val metarConfigJson = Json { ignoreUnknownKeys = true }

fun parseMetarConfigData(json: String): MetarConfigData =
    metarConfigJson.decodeFromString(MetarConfigData.serializer(), json)
