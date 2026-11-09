package dev.ame.polaris

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import polaris.shared.generated.resources.Res
import polaris.shared.generated.resources.montserrat
import polaris.shared.generated.resources.space_mono_bold
import polaris.shared.generated.resources.space_mono_regular
import polaris.shared.generated.resources.unbounded

/**
 * Retro-futuristic "spec sheet" style: a near-black page with flat, muted colour panels
 * (bento grid), ink-dark text on the panels, wide heavy headings and tiny mono labels.
 * No gradients or shine.
 *
 * The four panel colours live in [Panel] (ui/Gold.kt); this is the base dark scheme.
 */
object Palette {
    val Background = Color(0xFF0E0E0E) // the page
    val Card = Color(0xFF1A1A1A)       // dark cards, dialogs, nav
    val Text = Color(0xFFF2EFE9)       // main text on dark: warm off-white
    val Muted = Color(0xFF9C978F)      // secondary text on dark
    val Ink = Color(0xFF141414)        // text and lines on the coloured panels
    val Tangerine = Color(0xFFE9A25A)
    val Terracotta = Color(0xFFC9553D)
    val Sage = Color(0xFF8EA67A)
    val Stone = Color(0xFFB8B3AB)      // warm grey
}

private val SpecSheet = darkColorScheme(
    background = Palette.Background,
    onBackground = Palette.Text,

    surface = Palette.Card,
    onSurface = Palette.Text,
    onSurfaceVariant = Palette.Muted,
    surfaceVariant = Palette.Card,
    surfaceContainer = Palette.Card,
    surfaceContainerLow = Palette.Card,
    surfaceContainerHigh = Palette.Card,
    surfaceContainerHighest = Palette.Card,

    // The accent. Each page swaps this for its own panel colour (see PageTheme in App.kt).
    primary = Palette.Tangerine,
    onPrimary = Palette.Ink,
    secondary = Palette.Tangerine,
    onSecondary = Palette.Ink,
    secondaryContainer = Palette.Tangerine,
    onSecondaryContainer = Palette.Ink,

    error = Palette.Terracotta,
    onError = Palette.Ink,
    outline = Color(0x33F2EFE9),
)

/**
 * Three fonts (all SIL Open Font License, see licenses/ in the repo root):
 * - Unbounded: wide and heavy, for titles and big numbers ("CALENDAR", "25").
 * - Montserrat: plain and readable, for body text.
 * - Space Mono: small technical labels ("DUE IN 2 DAYS", "#01").
 */
@Composable
private fun displayFont() = FontFamily(
    Font(Res.font.unbounded, FontWeight.Medium),
    Font(Res.font.unbounded, FontWeight.SemiBold),
    Font(Res.font.unbounded, FontWeight.Bold),
    Font(Res.font.unbounded, FontWeight.Black),
)

@Composable
private fun bodyFont() = FontFamily(
    Font(Res.font.montserrat, FontWeight.Normal),
    Font(Res.font.montserrat, FontWeight.Medium),
    Font(Res.font.montserrat, FontWeight.SemiBold),
    Font(Res.font.montserrat, FontWeight.Bold),
)

@Composable
fun monoFont() = FontFamily(
    Font(Res.font.space_mono_regular, FontWeight.Normal),
    Font(Res.font.space_mono_bold, FontWeight.Bold),
)

@Composable
private fun polarisTypography(): Typography {
    val display = displayFont()
    val body = bodyFont()
    val mono = monoFont()
    val base = Typography()
    fun TextStyle.with(family: FontFamily, weight: FontWeight, size: Int? = null, spacing: Double? = null) = copy(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size?.sp ?: fontSize,
        letterSpacing = spacing?.em ?: letterSpacing,
    )
    return Typography(
        // Unbounded is very wide, so its sizes are a step smaller than Material's defaults.
        displayLarge = base.displayLarge.with(display, FontWeight.Black, 52),
        displayMedium = base.displayMedium.with(display, FontWeight.Black, 42),
        displaySmall = base.displaySmall.with(display, FontWeight.Bold, 34),
        headlineLarge = base.headlineLarge.with(display, FontWeight.Bold, 28),
        headlineMedium = base.headlineMedium.with(display, FontWeight.Bold, 24),
        headlineSmall = base.headlineSmall.with(display, FontWeight.Bold, 20),
        titleLarge = base.titleLarge.with(display, FontWeight.SemiBold, 18),
        titleMedium = base.titleMedium.with(display, FontWeight.SemiBold, 15),
        titleSmall = base.titleSmall.with(display, FontWeight.Medium, 13),
        bodyLarge = base.bodyLarge.with(body, FontWeight.Medium).copy(lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.with(body, FontWeight.Medium).copy(lineHeight = 21.sp),
        bodySmall = base.bodySmall.with(body, FontWeight.Medium),
        // Labels (buttons, chips, small captions) are mono, slightly spaced out
        labelLarge = base.labelLarge.with(mono, FontWeight.Bold, 13, 0.04),
        labelMedium = base.labelMedium.with(mono, FontWeight.Normal, 12, 0.04),
        labelSmall = base.labelSmall.with(mono, FontWeight.Normal, 10, 0.06),
    )
}

@Composable
fun PolarisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SpecSheet, typography = polarisTypography(), content = content)
}
