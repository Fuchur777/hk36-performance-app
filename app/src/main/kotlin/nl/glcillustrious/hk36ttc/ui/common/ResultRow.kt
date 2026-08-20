package nl.glcillustrious.hk36ttc.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Label on the left, the headline number on the right — used for every distance/weight
 * result across the app instead of a plain sentence, so the actual numbers stand out.
 *
 * [emphasized] false drops the bold, for figures that sit beside a headline number without
 * competing with it — the raw distances without safety margin, which are reference values
 * rather than the number a pilot plans on. */
@Composable
fun ResultRow(label: String, value: String, unit: String, emphasized: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        // weight(1f) so the label takes whatever is left instead of competing with the number
        // for intrinsic width — without it a long label wrapped raggedly on narrow screens.
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            "$value $unit",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/**
 * The four take-off/landing distances, grouped under "met marge" / "zonder marge" headings.
 *
 * The grouping exists to keep the labels short. They used to read "Grondloop (zonder marge)"
 * and "Over 15m obstakel (zonder marge)", which wrapped badly on a phone — worst inside a
 * runway card, which adds its own padding. Moving "zonder marge" into a heading says the same
 * thing once instead of twice, and leaves the row labels short enough to fit.
 *
 * It also puts the margin factor next to the numbers it produced. Previously the per-runway
 * cards never showed it at all — you had to remember what the stepper further up said.
 *
 * [withMarginTrailing] closes the *with-margin* group, not the block: in practice that is the
 * remaining runway length, which is computed from the with-margin distance
 * (`remainingWithMarginM`). Hanging it under the raw figures instead implied a relationship
 * that doesn't exist.
 */
/**
 * [groundRunWithMarginM]/[obstacleWithMarginM]/[groundRunRawM]/[obstacleRawM] carry an "M" for
 * historical reasons — the app was metric-only when this was written — but the caller is now
 * responsible for converting to the pilot's chosen distance unit *before* calling this, and
 * passing the matching [unitSuffix] ("m" or "ft"). This composable itself stays unit-agnostic:
 * it only formats and labels whatever numbers it's given. `Int`, not `Double`: every converted
 * distance is a whole number (see [nl.glcillustrious.hk36ttc.ui.common.displayDistance]).
 */
@Composable
fun DistanceResultBlock(
    marginFactor: Double,
    groundRunLabel: String,
    obstacleLabel: String,
    withMarginHeading: String,
    withoutMarginHeading: String,
    groundRunWithMarginM: Int,
    obstacleWithMarginM: Int,
    groundRunRawM: Int,
    obstacleRawM: Int,
    unitSuffix: String = "m",
    withMarginTrailing: @Composable () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        GroupHeading(withMarginHeading)
        ResultRow(groundRunLabel, groundRunWithMarginM.toString(), unitSuffix)
        ResultRow(obstacleLabel, obstacleWithMarginM.toString(), unitSuffix)
        withMarginTrailing()

        GroupHeading(withoutMarginHeading, topPadding = 10.dp)
        // Not bold: reference figures beside the two above, which are what the pilot plans on.
        ResultRow(groundRunLabel, groundRunRawM.toString(), unitSuffix, emphasized = false)
        ResultRow(obstacleLabel, obstacleRawM.toString(), unitSuffix, emphasized = false)
    }
}

/** A rule above each group heading — without it the two blocks ran together and the headings
 * read as just another row. The rule inherits the card's content colour at low alpha so it
 * works on every status colour a runway card can take. */
@Composable
private fun GroupHeading(text: String, topPadding: Dp = 0.dp) {
    Column(modifier = Modifier.padding(top = topPadding)) {
        HorizontalDivider(color = LocalContentColor.current.copy(alpha = 0.25f))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
        )
    }
}

/** One decimal — used only for [nl.glcillustrious.hk36ttc.ui.common.FlightContextCard]'s margin
 * *factor* heading (e.g. "1.33x"), which is dimensionless and unrelated to the unit-conversion
 * feature. Every actual distance is a whole number now (see [DistanceResultBlock]'s KDoc) and
 * formats with a plain `.toString()` instead of this. */
fun formatDistance(value: Double): String = "%.1f".format(value)

/** Remaining runway reads better with an explicit sign; the sign is the whole point. Whole
 * number, like every other converted distance. */
fun formatSignedDistance(value: Int): String = if (value >= 0) "+$value" else value.toString()
