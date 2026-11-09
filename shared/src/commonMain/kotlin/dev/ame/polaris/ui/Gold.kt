package dev.ame.polaris.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.ame.polaris.Palette

// (This file used to hold the shiny gold. The style is flat now, but the names stayed,
// so every screen that says "Gold.Shine" simply gets the current accent colour.)

/** The four flat panel colours of the bento grid. */
object Panel {
    val Tangerine = Palette.Tangerine
    val Terracotta = Palette.Terracotta
    val Sage = Palette.Sage
    val Stone = Palette.Stone
}

/** Corner radius of every card and panel: big and chunky, like the reference. */
val PanelShape = RoundedCornerShape(24.dp)

object Gold {
    /** The current accent as a fill (the page's colour, or ink inside a coloured panel). */
    val Shine: Brush
        @Composable @ReadOnlyComposable
        get() = SolidColor(MaterialTheme.colorScheme.primary)

    /** A hairline outline that suits whatever it's drawn on. */
    val Edge: Brush
        @Composable @ReadOnlyComposable
        get() = SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
}

/** How solid dark cards are. (Coloured panels are always solid.) */
const val CardAlpha = 1f

/** Fills the element with the accent colour, clipped to [shape]. */
@Composable
fun Modifier.goldShine(shape: Shape = RectangleShape): Modifier = clip(shape).background(Gold.Shine)

/**
 * Inside a coloured panel, everything switches to ink: text, icons, checkboxes, buttons
 * and highlights all become near-black on the panel's colour. Wrapping content in this
 * is what makes any screen piece "just work" on tangerine, sage, terracotta or stone.
 */
@Composable
fun PanelTheme(color: Color, content: @Composable () -> Unit) {
    val ink = Palette.Ink
    val scheme = MaterialTheme.colorScheme.copy(
        surface = color,
        onSurface = ink,
        onSurfaceVariant = ink.copy(alpha = 0.66f),
        onBackground = ink,
        primary = ink,
        onPrimary = color,
        secondaryContainer = ink,
        onSecondaryContainer = color,
        error = Color(0xFF7A1E0E),
        outline = ink.copy(alpha = 0.35f),
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalContentColor provides ink, content = content)
    }
}

/**
 * The app's card. With no [color] it's a dark card with a hairline outline; with a
 * [color] it's a solid bento panel with ink content. [highlighted] adds an accent outline.
 */
@Composable
fun PolarisCard(
    modifier: Modifier = Modifier,
    color: Color? = null,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (color != null) {
        PanelTheme(color) {
            Card(
                modifier = modifier,
                shape = PanelShape,
                colors = CardDefaults.cardColors(containerColor = color, contentColor = Palette.Ink),
                border = if (highlighted) BorderStroke(2.dp, Palette.Ink) else null,
                content = content,
            )
        }
    } else {
        Card(
            modifier = modifier,
            shape = PanelShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(
                if (highlighted) 2.dp else 1.dp,
                if (highlighted) Gold.Shine else Gold.Edge,
            ),
            content = content,
        )
    }
}

/** A filled button in the accent colour. */
@Composable
fun GoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
}

/** A round filled icon button in the accent colour. */
@Composable
fun GoldIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
}
