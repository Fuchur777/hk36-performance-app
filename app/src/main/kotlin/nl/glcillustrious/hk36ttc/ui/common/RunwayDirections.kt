package nl.glcillustrious.hk36ttc.ui.common

import nl.glcillustrious.hk36ttc.core.metar.RunwayCandidate
import nl.glcillustrious.hk36ttc.data.local.RunwayStripEntity

/**
 * One usable direction of a [RunwayStripEntity] — the strip stores only direction A's
 * heading/slope (see that entity's KDoc for why), so this is where direction B's heading+180
 * and sign-flipped slope actually get computed, once, for every screen to share.
 *
 * [id] is a stable, app-generated key (`"<stripId>A"`/`"<stripId>B"`) used for selection and
 * persistence — [designator] is the free-text label the pilot typed in (e.g. "02 gras") and is
 * only for display. Two strips can easily share the same [designator] (a grass and an asphalt
 * runway both numbered "02" is normal), so [designator] must never be used as a lookup key —
 * see [nl.glcillustrious.hk36ttc.core.metar.RunwayCandidate]'s KDoc for the bug this caused.
 */
data class RunwayDirectionOption(
    val strip: RunwayStripEntity,
    val id: String,
    val designator: String,
    val headingDegTrue: Double,
    val slopePct: Double
)

fun RunwayStripEntity.directionOptions(): List<RunwayDirectionOption> {
    val a = RunwayDirectionOption(this, "${id}A", designatorA, headingDegTrueA, slopePctA)
    if (oneWay) return listOf(a)
    val b = RunwayDirectionOption(this, "${id}B", designatorB, (headingDegTrueA + 180.0) % 360.0, -slopePctA)
    return listOf(a, b)
}

fun RunwayDirectionOption.toCandidate(): RunwayCandidate =
    RunwayCandidate(designator = id, headingDegTrue = headingDegTrue, lengthM = strip.lengthM, slopePct = slopePct, label = designator)

private fun zeroPadded(number: Int): String = if (number < 10) "0$number" else number.toString()

/**
 * A runway's two designators name the same magnetic tens digit as their heading, 18 apart
 * (ICAO Annex 14 convention) — the same fact this file already uses to derive direction B's
 * heading/slope from A instead of asking for them twice. This derives what B's *designator*
 * should be, so the edit dialog can prefill it the same way as the pilot types A.
 *
 * Handles the two things a real designator can carry beyond the bare number: an L/C/R side
 * suffix (swapped — L<->R, C stays C) and free trailing text used to tell two same-numbered
 * strips apart, e.g. "02 gras" -> "20 gras" (see airfield_edit_runway_naming_hint and
 * [RunwayDirectionsTest]'s "02 gras"/"20 gras" fixture).
 *
 * Works for either end as the one typed first — "27" derives "09" just as "09" derives "27" —
 * so there's no need to force the pilot to always enter the lower number as A.
 *
 * Returns null when [designatorA] doesn't start with a valid runway number (1-36): nothing to
 * derive from, so the caller should leave whatever the pilot already has in B alone.
 */
fun deriveOppositeDesignator(designatorA: String): String? {
    val trimmed = designatorA.trim()
    val digits = trimmed.takeWhile { it.isDigit() }
    if (digits.isEmpty() || digits.length > 2) return null
    val number = digits.toIntOrNull()?.takeIf { it in 1..36 } ?: return null

    var rest = trimmed.drop(digits.length)
    val suffix = rest.firstOrNull()?.uppercaseChar()?.takeIf { it in "LCR" }
    if (suffix != null) rest = rest.drop(1)

    val oppositeNumber = if (number <= 18) number + 18 else number - 18
    val oppositeSuffix = when (suffix) {
        'L' -> "R"
        'R' -> "L"
        'C' -> "C"
        else -> ""
    }
    return zeroPadded(oppositeNumber) + oppositeSuffix + rest
}

/**
 * Zero-pads a designator's leading runway number to two digits — "2" -> "02", "2 gras" ->
 * "02 gras" — without touching anything else. Meant to run on focus-loss, not on every
 * keystroke: padding "1" to "01" while the pilot is still typing "18" would make "18"
 * impossible to enter, the same reason [nl.glcillustrious.hk36ttc.ui.common.IntStepperField]
 * only clamps on blur/button-press rather than live.
 */
fun padDesignatorNumber(designator: String): String {
    val trimmed = designator.trim()
    val digits = trimmed.takeWhile { it.isDigit() }
    val number = digits.toIntOrNull()?.takeIf { it in 1..9 } ?: return trimmed
    return "0$number" + trimmed.drop(digits.length)
}
