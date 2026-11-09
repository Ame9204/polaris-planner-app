package dev.ame.polaris

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Black = Color(0xFF000000)
private val Navy = Color(0xFF14213D)
private val Gold = Color(0xFFFCD512)
private val LightGray = Color(0xFFE5E5E5)
private val White = Color(0xFFFFFFFF)

private val NightSky = darkColorScheme(
    background = Black,          // 000000: app background
    onBackground = White,        // text on the background

    surface = Navy,              // 14213D: cards, sheets, nav bar
    onSurface = White,
    onSurfaceVariant = LightGray, // E5E5E5: secondary text (dates, hints)
    surfaceVariant = Navy,
    surfaceContainer = Navy,
    surfaceContainerLow = Navy,
    surfaceContainerHigh = Navy,
    surfaceContainerHighest = Navy,

    primary = Gold,              // gold: buttons, highlights, selected tab
    onPrimary = Black,           // text on gold buttons
    secondary = Gold,
    onSecondary = Black,

    secondaryContainer = Gold,   // pill behind the selected tab
    onSecondaryContainer = Black, // icon on that pill
)

@Composable
fun PolarisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightSky, content = content)
}