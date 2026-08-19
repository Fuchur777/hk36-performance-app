package nl.glcillustrious.hk36ttc.core.airport

/**
 * The only two surfaces this aircraft's performance tables distinguish. Deliberately a core
 * type rather than the app's `RunwaySurfaceType`, so `:core` stays free of Room/app types; the
 * app maps one to the other at the import boundary.
 */
enum class ParsedRunwaySurface { ASPHALT, GRASS }

/**
 * One physical runway strip from `runways.csv`, already reduced to the app's own strip model
 * (one row = one strip = two usable directions, see `RunwayStripEntity`).
 *
 * [headingDerived] is the honest part of this import. See [parseRunwaysCsv] for why it exists
 * and what the caller is expected to do with it.
 */
data class ParsedRunway(
    val airportIdent: String,
    val designatorA: String,
    val designatorB: String?,
    val headingDegTrueA: Double,
    val headingDerived: Boolean,
    val lengthM: Double,
    val surface: ParsedRunwaySurface,
    val oneWay: Boolean
)

/**
 * Surfaces the aircraft simply cannot use. Mapping these to grass would not be conservative,
 * it would be wrong — a floatplane lane is not a short grass strip.
 */
private val UNUSABLE_SURFACE_KEYWORDS = listOf("WATER", "WTR", "SNOW", "ICE")

/**
 * Hard-surface markers. `UNPAVED` is checked separately *before* these, because it contains
 * `PAVED` and would otherwise map a soft field to asphalt — the one direction of error this
 * import must never make.
 */
private val ASPHALT_KEYWORDS = listOf("ASP", "ASF", "BIT", "CON", "PEM", "TAR", "PAVED")

/**
 * Soft-surface markers. Not exhaustive on purpose: anything unrecognised also lands on grass
 * (see [surfaceFor]), so this list only has to catch what should *definitely* not be mistaken
 * for asphalt.
 */
private val GRASS_KEYWORDS = listOf("GRAS", "GRS", "TURF", "GRAV", "GVL", "GRVL", "DIRT", "SAND", "CLAY", "EARTH", "SOIL", "GROUND", "UNPAVED")

/**
 * Maps OurAirports' free-text `surface` column onto the two categories the AFM knows.
 *
 * That column is genuinely unnormalised — real values include `ASPH`, `ASPH-G`, `Turf`, `TURF`,
 * `GVL`, `GRVL`, `GRAVEL` — so this works on keywords rather than an exact-match table.
 *
 * Two rules carry the safety of this whole import:
 * - **Unrecognised maps to grass.** Grass costs at least 20% extra distance (AFM §5.3.3), so
 *   guessing grass can only ever over-state the runway a pilot needs, never under-state it.
 * - **When both a hard and a soft marker appear, grass wins** — same reason.
 *
 * Returns null for surfaces the aircraft can't use at all, which the caller drops.
 */
internal fun surfaceFor(raw: String?): ParsedRunwaySurface? {
    val value = raw?.uppercase() ?: return ParsedRunwaySurface.GRASS
    if (UNUSABLE_SURFACE_KEYWORDS.any { value.contains(it) }) return null

    val looksSoft = GRASS_KEYWORDS.any { value.contains(it) }
    if (looksSoft) return ParsedRunwaySurface.GRASS

    val looksHard = ASPHALT_KEYWORDS.any { value.contains(it) }
    return if (looksHard) ParsedRunwaySurface.ASPHALT else ParsedRunwaySurface.GRASS
}

/**
 * Turns a runway designator into a heading: `04` -> 40°, `27L` -> 270°, `36` -> 0°.
 * Returns null for anything not built on a number in 1..36 — `H1` (helipad), `N`/`S` (compass
 * designators), and similar.
 */
internal fun headingFromDesignator(designator: String): Double? {
    val digits = designator.takeWhile { it.isDigit() }
    if (digits.isEmpty()) return null
    val number = digits.toIntOrNull() ?: return null
    if (number < 1 || number > 36) return null
    return (number * 10).mod(360).toDouble()
}

/**
 * Parses OurAirports `runways.csv` into strips this app can store directly. [lines] must start
 * with the header row; see [parseAirportsCsv] for the robustness reasoning, which is identical
 * here — including that this list-returning form is the convenient one, not the one to load the
 * real catalogue with. Use [forEachRunway] for that.
 *
 * **On the derived heading.** The advisor needs a *true* heading to resolve head- and crosswind
 * (rekenlogica.md §8), but `le_heading_degT` is empty for most rows in this dataset. Rather than
 * import runways that can't be calculated with, a missing heading is reconstructed from the
 * designator — and flagged via [ParsedRunway.headingDerived], because a designator is a rounded
 * *magnetic* heading, not a true one. That is a couple of degrees in the Netherlands but up to
 * twenty elsewhere, which is enough to matter for a crosswind component. Callers are expected to
 * surface the count of derived headings and tell the pilot to check them.
 *
 * A row is dropped when it is closed, has no usable length, has an unusable surface, or has
 * neither a true heading nor a numeric designator to reconstruct one from — in that last case
 * storing 0° would be a silent lie the app would then quietly calculate with.
 */
fun parseRunwaysCsv(lines: Sequence<String>): List<ParsedRunway> =
    buildList { forEachRunway(lines) { add(it) } }

/**
 * Streaming form of [parseRunwaysCsv] — see [forEachAirport] for why this shape exists and why
 * it is `inline`.
 */
inline fun forEachRunway(lines: Sequence<String>, action: (ParsedRunway) -> Unit) {
    val iterator = lines.iterator()
    if (!iterator.hasNext()) return
    val header = CsvHeader.parse(iterator.next())
    while (iterator.hasNext()) {
        val runway = parseRunwayRow(header, iterator.next()) ?: continue
        action(runway)
    }
}

/** One data row, or null when it is blank, closed, or fails any of the drop rules above. */
fun parseRunwayRow(header: CsvHeader, line: String): ParsedRunway? {
    if (line.isBlank()) return null
    val fields = CsvReader.splitLine(line)

    if (header.value(fields, "closed") == "1") return null

    val airportIdent = header.value(fields, "airport_ident") ?: return null
    val designatorA = header.value(fields, "le_ident") ?: return null

    val lengthM = header.value(fields, "length_ft")?.toDoubleOrNull()?.times(FEET_TO_METRES)
    if (lengthM == null || lengthM <= 0.0) return null

    val surface = surfaceFor(header.value(fields, "surface")) ?: return null

    val trueHeading = header.value(fields, "le_heading_degT")?.toDoubleOrNull()
    val heading: Double
    val derived: Boolean
    if (trueHeading != null) {
        heading = trueHeading.mod(360.0)
        derived = false
    } else {
        heading = headingFromDesignator(designatorA) ?: return null
        derived = true
    }

    val designatorB = header.value(fields, "he_ident")
    return ParsedRunway(
        airportIdent = airportIdent,
        designatorA = designatorA,
        designatorB = designatorB,
        headingDegTrueA = heading,
        headingDerived = derived,
        lengthM = lengthM,
        surface = surface,
        // The app's strip model already supports one-way strips; a missing high end is
        // exactly that case.
        oneWay = designatorB == null
    )
}
