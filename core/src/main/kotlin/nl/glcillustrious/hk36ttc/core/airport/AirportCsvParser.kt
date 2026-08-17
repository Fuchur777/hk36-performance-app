package nl.glcillustrious.hk36ttc.core.airport

/** Feet to metres — the OurAirports elevation column is in feet. */
internal const val FEET_TO_METRES = 0.3048

/**
 * One airport from `airports.csv`, reduced to the fields this app has any use for.
 *
 * [elevationM] is nullable on purpose. An empty `elevation_ft` means "not recorded", and
 * defaulting that to 0 would be a silent claim of sea level — which flows straight into the
 * pressure-altitude derivation (rekenlogica.md §8b) and from there into every distance. Null
 * forces the app-side to leave the field for the pilot to fill in instead.
 *
 * [latitudeDeg]/[longitudeDeg] are parsed but **nothing reads them**. They were added for a
 * nearest-airfield-by-GPS lookup that has since been dropped (see docs/00-plan.md); they stay
 * only because removing them would force a catalogue version bump and a re-seed on every
 * device for a ~1.4 MB saving. Don't treat their presence as a plan to use them.
 */
data class ParsedAirport(
    val ident: String,
    val type: String,
    val name: String,
    val elevationM: Double?,
    val isoCountry: String?,
    val municipality: String?,
    val icaoCode: String?,
    val gpsCode: String?,
    val localCode: String?,
    val latitudeDeg: Double?,
    val longitudeDeg: Double?
)

/**
 * Parses OurAirports `airports.csv`. [lines] is a sequence so the caller can stream a 12 MB
 * file straight off disk or the network without ever holding it whole in memory; the first
 * element must be the header row.
 *
 * Closed airports are dropped — they cannot be flown to, and leaving them in would only pad
 * the search results the pilot has to scan past.
 *
 * Rows that can't yield a usable record (no ident, or no name) are skipped rather than
 * throwing: this is a third-party file regenerated daily, and one malformed row should never
 * cost the pilot the entire catalogue.
 */
fun parseAirportsCsv(lines: Sequence<String>): List<ParsedAirport> {
    val iterator = lines.iterator()
    if (!iterator.hasNext()) return emptyList()
    val header = CsvHeader.parse(iterator.next())

    val airports = mutableListOf<ParsedAirport>()
    while (iterator.hasNext()) {
        val line = iterator.next()
        if (line.isBlank()) continue
        val fields = CsvReader.splitLine(line)

        val ident = header.value(fields, "ident") ?: continue
        val name = header.value(fields, "name") ?: continue
        val type = header.value(fields, "type") ?: "unknown"
        if (type == "closed") continue

        airports += ParsedAirport(
            ident = ident,
            type = type,
            name = name,
            elevationM = header.value(fields, "elevation_ft")?.toDoubleOrNull()?.times(FEET_TO_METRES),
            isoCountry = header.value(fields, "iso_country"),
            municipality = header.value(fields, "municipality"),
            icaoCode = header.value(fields, "icao_code")?.uppercase(),
            gpsCode = header.value(fields, "gps_code")?.uppercase(),
            localCode = header.value(fields, "local_code")?.uppercase(),
            latitudeDeg = header.value(fields, "latitude_deg")?.toDoubleOrNull(),
            longitudeDeg = header.value(fields, "longitude_deg")?.toDoubleOrNull()
        )
    }
    return airports
}
