package nl.schellenberg.hk36ttc.core.metar

import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A METAR only encodes day-of-month + time (no month/year), so its age can only be resolved
 * against a reference "now". Used for the mandatory staleness display at the confirmation step
 * (rekenlogica.md §9, threshold from [MetarConfigData.staleAfterMinutes]).
 */
object MetarAge {

    /** Assumes the observation falls within the current UTC month; if resolving the
     * day-of-month against [now] would land more than an hour in the future (e.g. querying on
     * the 1st for a METAR from day 28 of the previous month), rolls back one month instead. */
    fun minutesSince(metar: ParsedMetar, now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): Long {
        var observed = now
            .withDayOfMonth(1)
            .plusDays((metar.observationDayOfMonth - 1).toLong())
            .withHour(metar.observationHourUtc)
            .withMinute(metar.observationMinuteUtc)
            .withSecond(0)
            .withNano(0)
        if (observed.isAfter(now.plusHours(1))) {
            observed = observed.minusMonths(1)
        }
        // Floored at zero: a device clock a few minutes behind UTC, or a station reporting
        // slightly ahead, lands inside the one-hour tolerance above and would otherwise produce a
        // negative age — rendered as "-4 minuten" and never able to trip the staleness threshold.
        return Duration.between(observed, now).toMinutes().coerceAtLeast(0)
    }
}
