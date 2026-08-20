package nl.schellenberg.hk36ttc.core.metar

/**
 * Pressure altitude derived from field elevation and METAR QNH — new **[APP]** logic
 * (rekenlogica.md's pressure-altitude section), replacing a manual pilot calculation that
 * previously had to happen outside the app entirely.
 *
 * ISA standard pressure (1013.25 hPa) and the ~8.23 m-per-hPa relationship near sea level are
 * universal atmospheric constants, not AFM-tunable values — kept as literals here rather than
 * in metar_config.json, the same way [nl.schellenberg.hk36ttc.core.metar]'s inHg/hPa and
 * m/s-to-knots conversion factors are literals in [MetarParser].
 */
object PressureAltitude {
    private const val ISA_STANDARD_PRESSURE_HPA = 1013.25
    private const val METERS_PER_HPA = 8.23

    fun fromElevationAndQnh(elevationM: Double, qnhHpa: Double): Double =
        elevationM + (ISA_STANDARD_PRESSURE_HPA - qnhHpa) * METERS_PER_HPA
}
