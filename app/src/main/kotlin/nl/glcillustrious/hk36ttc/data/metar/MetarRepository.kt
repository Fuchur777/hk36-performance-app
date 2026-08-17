package nl.glcillustrious.hk36ttc.data.metar

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.glcillustrious.hk36ttc.core.metar.MetarConfigData
import nl.glcillustrious.hk36ttc.core.metar.MetarFeed
import nl.glcillustrious.hk36ttc.data.local.AircraftProfileRepository
import nl.glcillustrious.hk36ttc.data.local.AirfieldEntity

/** What a fetch attempt did, so the UI can say something specific rather than just "done". */
sealed interface MetarFetchResult {
    /** [updated] airfields got a fresh report; [withoutReport] had no observation available. */
    data class Success(val updated: Int, val withoutReport: Int) : MetarFetchResult

    /** No airfield had a station code to look up. */
    data object NoStations : MetarFetchResult

    /** Online lookup is switched off in `metar_config.json`. */
    data object Disabled : MetarFetchResult

    /** Network or service failure — the stored METARs are left untouched. */
    data class Failed(val reason: String) : MetarFetchResult
}

/**
 * Fetches METARs online and stores them on the pilot's airfields, replacing the manual
 * paste-it-yourself step (docs/00-plan.md Fase 2c).
 *
 * Stays true to the app's offline-first rule: this is strictly an *extra*. Every failure path
 * leaves whatever METAR is already stored exactly as it was, so a pilot with no signal falls
 * back to the previous report — or to typing the weather by hand — instead of losing data or
 * being blocked. The endpoint itself comes from [MetarConfigData], never from a literal here, so
 * it can be repointed or disabled without a rebuild.
 */
class MetarRepository(
    private val userRepository: AircraftProfileRepository,
    /** Fetches the body of a URL. Injected so the whole class is testable on the JVM. */
    private val fetch: (String) -> String = ::httpGet
) {

    /**
     * Looks up the given airfields in one request and stores what comes back.
     *
     * Batched deliberately: the service takes a comma-separated station list, so refreshing five
     * airfields is one round trip rather than five — which matters on the flaky mobile signal a
     * gliding site tends to have.
     */
    suspend fun refresh(airfields: List<AirfieldEntity>, config: MetarConfigData): MetarFetchResult =
        withContext(Dispatchers.IO) {
            val byStation = airfields.mapNotNull { airfield ->
                airfield.stationCode()?.let { it to airfield }
            }
            if (byStation.isEmpty()) return@withContext MetarFetchResult.NoStations

            val url = config.fetchUrlFor(byStation.map { it.first }.distinct())
                ?: return@withContext MetarFetchResult.Disabled

            val response = try {
                fetch(url)
            } catch (e: IOException) {
                return@withContext MetarFetchResult.Failed(e.message ?: "onbekende netwerkfout")
            }

            val reports = MetarFeed.splitByStation(response)
            val now = System.currentTimeMillis()
            var updated = 0
            var withoutReport = 0

            byStation.forEach { (station, airfield) ->
                val raw = reports[station]
                if (raw == null) {
                    withoutReport++
                    return@forEach
                }
                // Only write when the text actually changed: an unchanged report should not
                // reset the "entered at" stamp, or a stale observation would keep looking fresh.
                if (raw == airfield.metarRaw) return@forEach
                userRepository.saveAirfield(
                    airfield.copy(metarRaw = raw, metarEnteredAtEpochMs = now)
                )
                updated++
            }

            MetarFetchResult.Success(updated = updated, withoutReport = withoutReport)
        }

    /** Convenience for the single-airfield case (the airfield editor and the calc screens). */
    suspend fun refreshOne(airfield: AirfieldEntity, config: MetarConfigData): MetarFetchResult =
        refresh(listOf(airfield), config)

    companion object {
        /**
         * The station to read weather from: the explicitly chosen one where set, otherwise the
         * field's own ICAO. Most Dutch gliding sites have no station of their own and borrow a
         * neighbour's (Terlet -> EHDL), which is why the two are separate fields on
         * [AirfieldEntity] in the first place.
         */
        fun AirfieldEntity.stationCode(): String? =
            (metarStationIcao?.trim()?.ifBlank { null } ?: icao?.trim()?.ifBlank { null })
                ?.uppercase()
                ?.takeIf { it.length == 4 }

        /**
         * True when [airfield]'s stored METAR is old enough (or missing) that a calculation
         * screen should quietly fetch a fresh one.
         */
        fun shouldAutoRefresh(airfield: AirfieldEntity, config: MetarConfigData, nowMs: Long): Boolean {
            if (airfield.stationCode() == null) return false
            if (airfield.metarRaw.isNullOrBlank()) return true
            val fetchedAt = airfield.metarEnteredAtEpochMs ?: return true
            val ageMinutes = (nowMs - fetchedAt) / 60_000
            return ageMinutes >= config.autoRefreshAfterMinutes
        }

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
