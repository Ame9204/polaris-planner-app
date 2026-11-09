package dev.ame.polaris

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import polaris.shared.generated.resources.Res
import polaris.shared.generated.resources.special_elite

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

/**
 * Special Elite, a typewriter font (Apache License 2.0, see licenses/ in the repo root).
 * It only comes in one weight, so every style uses it as-is.
 */
@Composable
private fun polarisFont() = FontFamily(
    Font(Res.font.special_elite, FontWeight.Normal),
)

/**
 * Material's standard text sizes, in our font; body text a little roomier.
 * Every style is forced to the font's one real weight: asking for SemiBold or Bold would
 * make Compose fake a bold version by smearing the letters, which looks muddy on a
 * typewriter face. Headings stand out by size instead. (The weights listed below are
 * what a font with more weights would use, if you switch fonts later.)
 */
@Composable
private fun polarisTypography(): Typography {
    val font = polarisFont()
    val base = Typography()
    @Suppress("UNUSED_PARAMETER")
    fun TextStyle.f(weight: FontWeight? = null) = copy(fontFamily = font, fontWeight = FontWeight.Normal)
    return Typography(
        displayLarge = base.displayLarge.f(),
        displayMedium = base.displayMedium.f(),
        displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.f(FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.f(FontWeight.SemiBold),
        titleLarge = base.titleLarge.f(FontWeight.SemiBold),
        titleMedium = base.titleMedium.f(FontWeight.SemiBold),
        titleSmall = base.titleSmall.f(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.f().copy(lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.f().copy(lineHeight = 22.sp),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(FontWeight.SemiBold),
        labelMedium = base.labelMedium.f(FontWeight.Medium),
        labelSmall = base.labelSmall.f(FontWeight.Medium),
    )
}

@Composable
fun PolarisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightSky, typography = polarisTypography(), content = content)
}