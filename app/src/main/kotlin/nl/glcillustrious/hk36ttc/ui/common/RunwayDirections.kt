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
