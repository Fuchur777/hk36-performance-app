package nl.glcillustrious.hk36ttc.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

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
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            "$value $unit",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal
        )
    }
}
