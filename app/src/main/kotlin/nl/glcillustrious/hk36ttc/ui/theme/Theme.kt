package nl.glcillustrious.hk36ttc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Result-card colors that Material3's ColorScheme has no dedicated slot for
 * (see look-and-feel.md "Componenten-richtlijnen": success/warning/error status cards).
 */
data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val caution: Color,
    val onCaution: Color,
    val warning: Color,
    val onWarning: Color,
    val error: Color,
    val onError: Color
)

private val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(
        success = StatusSuccess,
        onSuccess = OnDark,
        caution = StatusCaution,
        onCaution = OnStatusCaution,
        warning = StatusWarning,
        onWarning = OnStatusWarning,
        error = StatusError,
        onError = OnLight
    )
}

val MaterialTheme.status: StatusColors
    @Composable
    get() = LocalStatusColors.current

private val DarkColors = darkColorScheme(
    primary = IllustriousBlue,
    onPrimary = OnDark,
    secondary = IllustriousOrange,
    onSecondary = OnDark,
    background = BackgroundDark,
    onBackground = OnDark,
    surface = SurfaceDark,
    onSurface = OnDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = OnDarkVariant,
    outline = OutlineDark,
    error = StatusError,
    onError = OnLight
)

private val LightColors = lightColorScheme(
    primary = IllustriousBlue,
    onPrimary = OnDark,
    secondary = IllustriousOrange,
    onSecondary = OnDark,
    background = BackgroundLight,
    onBackground = OnLight,
    surface = Color.White,
    onSurface = OnLight,
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF5C5C5C),
    outline = Color(0xFFCCCCCC),
    error = StatusError,
    onError = OnLight
)

private val darkStatusColors = StatusColors(
    success = StatusSuccess,
    onSuccess = OnDark,
    caution = StatusCaution,
    onCaution = OnStatusCaution,
    warning = StatusWarning,
    onWarning = OnStatusWarning,
    error = StatusError,
    onError = OnLight
)

private val lightStatusColors = darkStatusColors.copy()

@Composable
fun Hk36ttcTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val statusColors = if (darkTheme) darkStatusColors else lightStatusColors

    CompositionLocalProvider(LocalStatusColors provides statusColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }
}
