package nl.glcillustrious.hk36ttc.ui.theme

import androidx.compose.ui.graphics.Color

// Illustrious brand palette, see docs/look-and-feel.md — pixel-extracted from
// GLC_ILLUSTRIOUS_Logo.png. Do not rename without updating that document.
val IllustriousBlue = Color(0xFF176FC1)
val IllustriousOrange = Color(0xFFF68712)

val BackgroundDark = Color(0xFF1A1A1A)
val SurfaceDark = Color(0xFF242424)
val OnDark = Color(0xFFFFFFFF)
val OnDarkVariant = Color(0xFFB0B0B0)
val OutlineDark = Color(0xFF3A3A3A)

val BackgroundLight = Color(0xFFFAFAFA)
val OnLight = Color(0xFF1A1A1A)

// Status colors — deliberately not the solid Illustrious accent, to avoid clashing with the
// primary action button color (look-and-feel.md "Componenten-richtlijnen"). W&B violations
// and warnings share one "needs attention" treatment (a light tint of the accent orange,
// not a full-saturation fill) — bold text within the result card is what distinguishes a
// hard violation from a soft advisory, not a separate color tier.
val StatusError = Color(0xFFCF6679)
val StatusWarning = Color(0xFFFCE1C0)
val OnStatusWarning = OnLight
val StatusSuccess = Color(0xFF4CAF50)
