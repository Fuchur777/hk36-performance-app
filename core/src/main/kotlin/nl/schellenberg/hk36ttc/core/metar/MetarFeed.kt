package nl.schellenberg.hk36ttc.core.metar

/**
 * Splits a raw multi-station METAR feed into one report per station.
 *
 * The app asks for several stations in one request (all the pilot's airfields at once), and the
 * service answers with one report per line — but **not in the order they were requested**, and
 * with nothing at all for stations that have no current observation. Matching by position would
 * therefore hand a pilot another field's weather, so everything is keyed on the station code
 * each report states about itself.
 *
 * Pure string handling, in `core` alongside the parser, so it can be tested without a network.
 */
object MetarFeed {

    /**
     * Maps station ICAO to its raw report line. Lines that do not parse are dropped rather than
     * guessed at; a report the app can't read is of no use to a calculation anyway.
     *
     * The keys come from the parsed report, so a response line is only ever filed under the
     * station it actually belongs to.
     */
    fun splitByStation(response: String): Map<String, String> =
        response.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parsed = MetarParser.parse(line) as? MetarParseResult.Success ?: return@mapNotNull null
                parsed.metar.stationIcao to line
            }
            // First wins, not last. A feed asked for one station can answer with several
            // observations for it, newest first; `toMap` on its own would silently keep whichever
            // came last — the *oldest* — and MetarRepository would then stamp that stale report as
            // freshly fetched. Dropping later duplicates keeps the current observation.
            .distinctBy { (station, _) -> station }
            .toMap()
}
