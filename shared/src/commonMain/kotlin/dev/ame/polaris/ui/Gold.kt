package dev.ame.polaris.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shiny gold: a gradient lit from the top-left, like light catching metal.
 * Brush.linearGradient with no start/end runs from the top-left corner of whatever
 * it's drawn on to the bottom-right, so it fits any size automatically.
 */
object Gold {
    // Subtle: the highlight and shadow sit close to the base gold. Push them further
    // apart (whiter highlight, darker deep) for a more metallic look.
    val Highlight = Color(0xFFFFE680) // where the light hits
    val Base = Color(0xFFFCD512)      // the theme's gold
    val Deep = Color(0xFFE0B400)      // the side turned away from the light

    /** For filled gold: buttons, the selected tab, today's date. */
    val Shine: Brush = Brush.linearGradient(
        0.0f to Highlight,
        0.3f to Base,
        1.0f to Deep,
    )

    /** For outlines: a soft glint at the top-left, fading toward the bottom-right. */
    val Edge: Brush = Brush.linearGradient(
        0.0f to Base.copy(alpha = 0.8f),
        0.4f to Base.copy(alpha = 0.45f),
        1.0f to Base.copy(alpha = 0.15f),
    )
}

/** How solid the navy cards are: 1 = fully solid, 0 = invisible. Lower shows more of the black behind. */
const val CardAlpha = 0.45f

/** Fills the element with shiny gold, clipped to [shape]. */
fun Modifier.goldShine(shape: Shape = RectangleShape): Modifier = clip(shape).background(Gold.Shine)

/**
 * The app's card: see-through navy with a gold outline.
 * [highlighted] makes the outline thicker (the selected note, the Now card).
 */
@Composable
fun PolarisCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = CardAlpha),
        ),
        border = BorderStroke(if (highlighted) 2.dp else 1.dp, Gold.Edge),
        content = content,
    )
}

/** A filled button in shiny gold (grey when disabled). */
@Composable
fun GoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = ButtonDefaults.shape
    Button(
        onClick = onClick,
        // The gold is painted behind a see-through button, because Button only accepts flat colours.
        modifier = if (enabled) modifier.goldShine(shape) else modifier,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
}

/** A round filled icon button in shiny gold (grey when disabled). */
@Composable
fun GoldIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val shape = IconButtonDefaults.filledShape
    FilledIconButton(
        onClick = onClick,
        modifier = if (enabled) modifier.goldShine(shape) else modifier,
        enabled = enabled,
        shape = shape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
}
