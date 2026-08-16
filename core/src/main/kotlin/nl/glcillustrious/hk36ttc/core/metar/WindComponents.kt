package nl.glcillustrious.hk36ttc.core.metar

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** [headwindKts] negative means tailwind — the AFM tables have no tailwind data (rekenlogica.md
 * §2.1), so a negative value here must block the calculation, never be clamped to zero.
 * [crosswindKts] is always non-negative (which side is irrelevant to any AFM limit check). */
data class WindComponentResult(val headwindKts: Double, val crosswindKts: Double)

/**
 * Headwind/crosswind decomposition of a METAR wind against a runway heading, per
 * rekenlogica.md §8. Both headings must be in the same reference frame — METAR wind direction
 * is always true north, so runway headings are stored as true too
 * ([nl.glcillustrious.hk36ttc.core.wb]-style: never mix true/magnetic silently).
 */
object WindComponents {

    fun compute(windDirectionDeg: Double, windSpeedKts: Double, runwayHeadingDegTrue: Double): WindComponentResult {
        val angleRad = Math.toRadians(angleBetween(windDirectionDeg, runwayHeadingDegTrue))
        return WindComponentResult(
            headwindKts = windSpeedKts * cos(angleRad),
            crosswindKts = windSpeedKts * abs(sin(angleRad))
        )
    }

    /** Smallest angle between two compass headings, normalized to [0, 180] — handles the
     * 350°-vs-010° wraparound case rekenlogica.md §8 requires. */
    private fun angleBetween(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }
}

/** `demonstrated_crosswind_kmh` in performance_normal.json/performance_tow.json is km/h
 * (matching the AFM's own units), while METAR wind speed here is always knots — 1 kt is
 * exactly 1.852 km/h by definition, not an approximation. */
fun kmhToKts(kmh: Double): Double = kmh / 1.852
