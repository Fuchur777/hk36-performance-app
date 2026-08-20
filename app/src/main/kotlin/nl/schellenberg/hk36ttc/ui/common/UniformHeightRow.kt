package nl.schellenberg.hk36ttc.ui.common

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier

/**
 * Makes a `SingleChoiceSegmentedButtonRow` size itself to its tallest child instead of
 * Compose's default (tall enough for exactly one line of text). Needed wherever a segment
 * label can wrap to two lines (e.g. "Zachte grond") next to segments that fit on one line —
 * without forcing an intrinsic-height measurement pass, the row's height is fixed before
 * wrapping is known and the two-line segment gets clipped.
 *
 * Apply this to the row itself; apply `Modifier.fillMaxHeight()` to each `SegmentedButton`
 * inside it so every segment stretches to match the tallest one.
 */
fun Modifier.uniformSegmentedRowHeight(): Modifier = this.height(IntrinsicSize.Max)
