package nl.schellenberg.hk36ttc.data.metar

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.schellenberg.hk36ttc.core.metar.MetarConfigData

/** What a historical fetch attempt did. */
sealed interface HistoricalMetarResult {
    data class Success(val rawMetar: String, val observedAtEpochMs: Long) : HistoricalMetarResult
    data object NotFound : HistoricalMetarResult
    data object Disabled : HistoricalMetarResult
    data class Failed(val reason: String) : HistoricalMetarResult
}

/**
 * Backfills a PAST recording's weather (Fase 4c), separate from [MetarRepository] because the
 * source and response shape genuinely differ: this queries the Iowa Environmental Mesonet's
 * ASOS archive (a `station,valid,metar` CSV, one row per hourly observation for a UTC calendar
 * day) rather than aviationweather.gov's current-observation-only batch lookup. Same offline-
 * first, injectable-fetch-lambda conventions as [MetarRepository] — a failed fetch changes
 * nothing, it just reports [HistoricalMetarResult.Failed] for the caller to show.
 */
class HistoricalMetarRepository(
    /** Fetches the body of a URL. Injected so the whole class is testable on the JVM. */
    private val fetch: (String) -> String = ::httpGet
) {

    /** Fetches every observation for [stationIcao] on [atEpochMs]'s UTC calendar day, and
     * returns the one closest to [atEpochMs] — IEM returns roughly hourly reports for a day, not
     * a single one, so "nearest" matters more than "first". */
    suspend fun fetchNearest(stationIcao: String, atEpochMs: Long, config: MetarConfigData): HistoricalMetarResult =
        withContext(Dispatchers.IO) {
            val dateUtc = Instant.ofEpochMilli(atEpochMs).atZone(ZoneOffset.UTC)
            val url = config.historicalFetchUrlFor(stationIcao, dateUtc.year, dateUtc.monthValue, dateUtc.dayOfMonth)
                ?: return@withContext HistoricalMetarResult.Disabled

            val response = try {
                fetch(url)
            } catch (e: Exception) {
                // Broad on purpose, same reasoning as MetarRepository.httpGet's caller: `fetch`
                // is injectable and even the default can throw more than IOException.
                return@withContext HistoricalMetarResult.Failed(e.message ?: "onbekende netwerkfout")
            }

            val observations = parseCsv(response)
            if (observations.isEmpty()) return@withContext HistoricalMetarResult.NotFound

            val nearest = observations.minBy { abs(it.first - atEpochMs) }
            HistoricalMetarResult.Success(rawMetar = nearest.second, observedAtEpochMs = nearest.first)
        }

    companion object {
        private val VALID_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /** Parses IEM's `station,valid,metar` CSV (`format=onlycomma`, one header row). Rows
         * with a missing report ("M", per `missing=M` in the query) or an unparseable timestamp
         * are skipped, not treated as an empty/zero-time METAR. `internal` for direct unit
         * testing. */
        internal fun parseCsv(response: String): List<Pair<Long, String>> =
            response.lineSequence()
                .drop(1) // header row: station,valid,metar
                .mapNotNull { line ->
                    val parts = line.split(",", limit = 3)
                    if (parts.size < 3) return@mapNotNull null
                    val (_, valid, metar) = parts
                    val trimmedMetar = metar.trim()
                    if (trimmedMetar.isBlank() || trimmedMetar == "M") return@mapNotNull null
                    val epochMs = try {
                        LocalDateTime.parse(valid.trim(), VALID_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli()
                    } catch (e: DateTimeParseException) {
                        return@mapNotNull null
                    }
                    epochMs to trimmedMetar
                }
                .toList()

        private fun httpGet(url: String): String {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP ${connection.responseCode}")
                }
                return connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
