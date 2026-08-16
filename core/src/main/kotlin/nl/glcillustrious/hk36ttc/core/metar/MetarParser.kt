package nl.glcillustrious.hk36ttc.core.metar

/**
 * A METAR decoded far enough to feed [nl.glcillustrious.hk36ttc.core.perf.PerformanceCalculator]
 * and [WindComponents] — not a full METAR decoder (clouds, visibility, weather phenomena are
 * ignored entirely, they're not needed for any calculation here).
 *
 * [windDirectionDeg] is `null` when the wind is reported variable (`VRB`) — the AFM tables need
 * a specific headwind component, and guessing one from a variable direction would silently
 * misrepresent the actual conditions (rekenlogica.md §5's "never proceed on unconfirmed input"
 * principle applies here too). A calm wind (`00000KT`) is represented as direction `0.0`/speed
 * `0.0` rather than variable, since the component is zero regardless of the assumed direction.
 *
 * [qnhHpa] is nullable: METARs occasionally omit the altimeter group, and that alone shouldn't
 * fail the whole parse — the app falls back to asking for pressure altitude manually.
 */
data class ParsedMetar(
    val stationIcao: String,
    val observationDayOfMonth: Int,
    val observationHourUtc: Int,
    val observationMinuteUtc: Int,
    val windDirectionDeg: Double?,
    val windVariableDirection: Boolean,
    val windSpeedKts: Double,
    val windGustKts: Double?,
    val temperatureC: Double,
    val dewpointC: Double?,
    val qnhHpa: Double?
)

/** Why a raw METAR string couldn't be decoded — typed rather than a literal message, same
 * pattern as [nl.glcillustrious.hk36ttc.core.perf.TowBlockReason]: `core` has no Android
 * resources to localize a string with, so the app layer maps each case to one. */
sealed interface MetarParseError {
    data object Empty : MetarParseError
    data object MissingStation : MetarParseError
    data object MissingObservationTime : MetarParseError
    data object MissingWind : MetarParseError
    data object MissingTemperature : MetarParseError
}

sealed interface MetarParseResult {
    data class Success(val metar: ParsedMetar) : MetarParseResult
    data class Failure(val reason: MetarParseError) : MetarParseResult
}

/**
 * Parses the handful of METAR groups this app actually needs. Deliberately tolerant of group
 * order (real METARs vary station-to-station) by scanning every whitespace-separated token
 * against each group's pattern, rather than assuming a fixed position.
 */
object MetarParser {

    private val TIME_REGEX = Regex("""^(\d{2})(\d{2})(\d{2})Z$""")
    private val WIND_REGEX = Regex("""^(\d{3}|VRB)(\d{2,3})(G(\d{2,3}))?(KT|MPS)$""")
    private val TEMP_REGEX = Regex("""^(M?\d{2})/(M?\d{2})$""")
    private val QNH_HPA_REGEX = Regex("""^Q(\d{3,4})$""")
    private val QNH_INHG_REGEX = Regex("""^A(\d{4})$""")
    private val STATION_REGEX = Regex("""^[A-Z]{4}$""")

    private const val INHG_TO_HPA = 33.8639
    private const val MPS_TO_KT = 1.94384

    fun parse(raw: String): MetarParseResult {
        val tokens = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return MetarParseResult.Failure(MetarParseError.Empty)

        val station = tokens.getOrNull(0)?.takeIf { STATION_REGEX.matches(it) }
            ?: return MetarParseResult.Failure(MetarParseError.MissingStation)

        val timeMatch = tokens.firstNotNullOfOrNull { TIME_REGEX.matchEntire(it) }
            ?: return MetarParseResult.Failure(MetarParseError.MissingObservationTime)
        val (day, hour, minute) = timeMatch.destructured

        val windMatch = tokens.firstNotNullOfOrNull { WIND_REGEX.matchEntire(it) }
            ?: return MetarParseResult.Failure(MetarParseError.MissingWind)
        val (dirToken, speedToken, _, gustToken, unit) = windMatch.destructured
        val unitFactor = if (unit == "MPS") MPS_TO_KT else 1.0
        val speedKts = speedToken.toDouble() * unitFactor
        val gustKts = gustToken.takeIf { it.isNotEmpty() }?.toDouble()?.times(unitFactor)
        val variable = dirToken == "VRB"
        val calm = !variable && dirToken == "000" && speedKts == 0.0
        val directionDeg = when {
            variable -> null
            else -> dirToken.toDouble()
        }

        val tempMatch = tokens.firstNotNullOfOrNull { TEMP_REGEX.matchEntire(it) }
            ?: return MetarParseResult.Failure(MetarParseError.MissingTemperature)
        val (tempToken, dewpointToken) = tempMatch.destructured

        val qnhHpa = tokens.firstNotNullOfOrNull { QNH_HPA_REGEX.matchEntire(it) }
            ?.groupValues?.get(1)?.toDouble()
            ?: tokens.firstNotNullOfOrNull { QNH_INHG_REGEX.matchEntire(it) }
                ?.groupValues?.get(1)?.toDouble()?.div(100.0)?.times(INHG_TO_HPA)

        return MetarParseResult.Success(
            ParsedMetar(
                stationIcao = station,
                observationDayOfMonth = day.toInt(),
                observationHourUtc = hour.toInt(),
                observationMinuteUtc = minute.toInt(),
                windDirectionDeg = if (calm) 0.0 else directionDeg,
                windVariableDirection = variable,
                windSpeedKts = speedKts,
                windGustKts = gustKts,
                temperatureC = parseSignedTemp(tempToken),
                dewpointC = parseSignedTemp(dewpointToken),
                qnhHpa = qnhHpa
            )
        )
    }

    private fun parseSignedTemp(token: String): Double =
        if (token.startsWith("M")) -token.substring(1).toDouble() else token.toDouble()
}
