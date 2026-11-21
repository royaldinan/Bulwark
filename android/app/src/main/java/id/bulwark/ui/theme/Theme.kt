package id.bulwark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BulwarkLightColorScheme = lightColorScheme(
    primary = BulwarkNavy,
    onPrimary = BulwarkWhite,
    secondary = BulwarkGradientEnd,
    onSecondary = BulwarkWhite,
    background = BulwarkWhite,
    onBackground = BulwarkNavy,
    surface = BulwarkWhite,
    onSurface = BulwarkNavy,
    surfaceVariant = BulwarkSurface,
    onSurfaceVariant = BulwarkNavy,
    outline = BulwarkLine,
    error = BulwarkError,
    onError = BulwarkWhite,
)

@Composable
fun BulwarkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BulwarkLightColorScheme,
        typography = BulwarkTypography,
        shapes = BulwarkShapes,
        content = content,
    )
}
