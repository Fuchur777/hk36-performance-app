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
    @SerialName("auto_refresh_after_minutes") val autoRefreshAfterMinutes: Int = 20,
    /**
     * Endpoint template for HISTORICAL lookups (Fase 4c — backfilling a past recording's
     * conditions), with `{station}`/`{year}`/`{month}`/`{day}` standing in for a single ICAO
     * code and a UTC calendar date. Separate from [fetchUrlTemplate] because the source and
     * response shape genuinely differ (a per-day archive query vs. a current-observation batch
     * lookup) — see [nl.schellenberg.hk36ttc.data.metar.HistoricalMetarRepository]. Same
     * "never hardcode a service URL in Kotlin" rule as [fetchUrlTemplate]; blank disables it.
     */
    @SerialName("historical_fetch_url_template") val historicalFetchUrlTemplate: String = DEFAULT_HISTORICAL_FETCH_URL
) {
    /** The URL to call for [stations], or null when the lookup has been switched off. */
    fun fetchUrlFor(stations: List<String>): String? {
        if (fetchUrlTemplate.isBlank() || stations.isEmpty()) return null
        return fetchUrlTemplate.replace("{stations}", stations.joinToString(","))
    }

    /** The URL to call for a historical lookup of [station] on [year]/[month]/[day] (UTC), or
     * null when the lookup has been switched off. */
    fun historicalFetchUrlFor(station: String, year: Int, month: Int, day: Int): String? {
        if (historicalFetchUrlTemplate.isBlank() || station.isBlank()) return null
        return historicalFetchUrlTemplate
            .replace("{station}", station)
            .replace("{year}", year.toString())
            .replace("{month}", month.toString())
            .replace("{day}", day.toString())
    }

    companion object {
        /**
         * aviationweather.gov — free, no account and no API key, named as the intended source in
         * docs/00-plan.md Fase 2c. `format=raw` yields one plain METAR per line.
         */
        const val DEFAULT_FETCH_URL = "https://aviationweather.gov/api/data/metar?ids={stations}&format=raw"

        /**
         * Iowa Environmental Mesonet's ASOS archive — free, no account, no API key, worldwide
         * coverage confirmed against a Dutch station (EHRD) during Fase 4c's own design pass.
         * `format=onlycomma` yields a `station,valid,metar` CSV, one row per observation for the
         * requested day.
         */
        const val DEFAULT_HISTORICAL_FETCH_URL =
            "https://mesonet.agron.iastate.edu/cgi-bin/request/asos.py?station={station}&data=metar" +
                "&year1={year}&month1={month}&day1={day}&year2={year}&month2={month}&day2={day}" +
                "&tz=Etc/UTC&format=onlycomma&latlon=no&elev=no&missing=M&trace=T&direct=no&report_type=3"

        /** Fallback only for tests/first-run seeding — never the runtime path (see
         * CalculationDataStore). 60 minutes matches rekenlogica.md §9's suggested threshold. */
        val DEFAULT = MetarConfigData(staleAfterMinutes = 60)
    }
}

private val metarConfigJson = Json { ignoreUnknownKeys = true }

fun parseMetarConfigData(json: String): MetarConfigData =
    metarConfigJson.decodeFromString(MetarConfigData.serializer(), json)
