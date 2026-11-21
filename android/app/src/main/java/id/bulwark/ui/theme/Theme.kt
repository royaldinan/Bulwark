package id.bulwark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Bulwark is a light-only brand: white surfaces, navy text, gradient accents.
private val BulwarkLightColors = lightColorScheme(
    primary = BulwarkGradientStart,
    onPrimary = Color.White,
    primaryContainer = BulwarkSelectedCard,
    onPrimaryContainer = BulwarkNavy,
    secondary = BulwarkGradientEnd,
    onSecondary = Color.White,
    background = BulwarkWhite,
    onBackground = BulwarkNavy,
    surface = BulwarkWhite,
    onSurface = BulwarkNavy,
    surfaceVariant = BulwarkSurface,
    onSurfaceVariant = BulwarkNavy,
    outline = BulwarkLine,
)

@Composable
fun BulwarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BulwarkLightColors,
        typography = BulwarkTypography,
        content = content,
    )
}
