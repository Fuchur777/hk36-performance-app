package nl.schellenberg.hk36ttc.core.perf

/**
 * Result of a grid interpolation: the value plus whether any input had to be clamped to the
 * published grid's edge — meaning the true answer is an out-of-range approximation, never a
 * silent extrapolation (rekenlogica.md §2.1).
 */
data class InterpolatedValue(val value: Double, val outOfRange: Boolean)

/**
 * Generic bilinear (OAT x pressure-altitude) and wind-blended interpolation over the AFM
 * performance grids. Used by both the normal and tow-plane take-off/landing tables — the
 * grid shape (a flat list of points with named fields) and interpolation method
 * (rekenlogica.md §2.1) are identical, only the source table and value field differ.
 */
object GridInterpolation {

    /**
     * Bilinear interpolation of a single field over a rectangular (oatC, pressureAltM) grid.
     * [points] must be exactly one rectangular OAT x altitude grid (e.g. already filtered to
     * a single headwind value, or the whole table if there's no wind dimension at all, like
     * landing). Inputs outside the published range are clamped to the nearest grid edge.
     */
    fun <T> bilinear(
        oatC: Double,
        pressureAltM: Double,
        points: List<T>,
        oatOf: (T) -> Double,
        altOf: (T) -> Double,
        valueOf: (T) -> Double
    ): InterpolatedValue {
        val oats = points.map(oatOf).distinct().sorted()
        val alts = points.map(altOf).distinct().sorted()

        val (clampedOat, oatOutOfRange) = clampToRange(oatC, oats)
        val (clampedAlt, altOutOfRange) = clampToRange(pressureAltM, alts)

        val (oat1, oat2) = bracket(clampedOat, oats)
        val (alt1, alt2) = bracket(clampedAlt, alts)

        fun valueAt(oat: Double, alt: Double): Double =
            points.first { oatOf(it) == oat && altOf(it) == alt }.let(valueOf)

        val v11 = valueAt(oat1, alt1)
        val v12 = valueAt(oat1, alt2)
        val v21 = valueAt(oat2, alt1)
        val v22 = valueAt(oat2, alt2)

        val tOat = if (oat2 == oat1) 0.0 else (clampedOat - oat1) / (oat2 - oat1)
        val tAlt = if (alt2 == alt1) 0.0 else (clampedAlt - alt1) / (alt2 - alt1)

        val vAlt1 = v11 + tOat * (v21 - v11)
        val vAlt2 = v12 + tOat * (v22 - v12)
        val result = vAlt1 + tAlt * (vAlt2 - vAlt1)

        return InterpolatedValue(result, oatOutOfRange || altOutOfRange)
    }

    /**
     * Bilinear on (oatC, pressureAltM) for the two nearest published headwind values, then
     * linear blend between them. This is §2.1's required order: "eerst bilineair
     * interpoleren op (OAT, drukhoogte) voor de twee omliggende windwaarden, dan lineair
     * interpoleren over wind."
     */
    fun <T> windBilinear(
        headwindKts: Double,
        oatC: Double,
        pressureAltM: Double,
        points: List<T>,
        windOf: (T) -> Double,
        oatOf: (T) -> Double,
        altOf: (T) -> Double,
        valueOf: (T) -> Double
    ): InterpolatedValue {
        val winds = points.map(windOf).distinct().sorted()
        val (clampedWind, windOutOfRange) = clampToRange(headwindKts, winds)
        val (w1, w2) = bracket(clampedWind, winds)

        val v1 = bilinear(oatC, pressureAltM, points.filter { windOf(it) == w1 }, oatOf, altOf, valueOf)
        val v2 = if (w2 == w1) v1 else bilinear(oatC, pressureAltM, points.filter { windOf(it) == w2 }, oatOf, altOf, valueOf)

        val blended = linear(clampedWind, w1, v1, w2, v2)
        return InterpolatedValue(blended.value, blended.outOfRange || windOutOfRange)
    }

    /** Linear interpolation between two already-computed grid results at [x1]/[x2]. */
    fun linear(x: Double, x1: Double, v1: InterpolatedValue, x2: Double, v2: InterpolatedValue): InterpolatedValue {
        if (x1 == x2) return v1
        val t = (x - x1) / (x2 - x1)
        return InterpolatedValue(
            value = v1.value + t * (v2.value - v1.value),
            outOfRange = v1.outOfRange || v2.outOfRange
        )
    }

    /** Clamps [x] into the range spanned by [values]; flags whether clamping occurred. */
    fun clampToRange(x: Double, values: List<Double>): InterpolatedValue {
        val min = values.min()
        val max = values.max()
        val clamped = x.coerceIn(min, max)
        return InterpolatedValue(clamped, clamped != x)
    }

    /** Returns the two adjacent grid values bracketing [x] (both equal if x matches one exactly). */
    private fun bracket(x: Double, sortedValues: List<Double>): Pair<Double, Double> {
        if (sortedValues.size == 1) return sortedValues[0] to sortedValues[0]
        for (i in 0 until sortedValues.size - 1) {
            if (x >= sortedValues[i] && x <= sortedValues[i + 1]) {
                return sortedValues[i] to sortedValues[i + 1]
            }
        }
        return sortedValues[sortedValues.size - 2] to sortedValues.last()
    }
}
