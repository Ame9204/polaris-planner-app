package dev.ame.polaris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

// Decorative "spec sheet" touches from the reference design: barcodes, a big arrow,
// outlined number boxes and tiny mono codes. Purely visual; they don't mean anything.

/**
 * A little barcode. The bar pattern comes from [seed], so the same text always gives
 * the same barcode (it doesn't flicker), and different panels get different ones.
 */
@Composable
fun Barcode(seed: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(width = 64.dp, height = 14.dp)) {
        var hash = seed.hashCode()
        var x = 0f
        val unit = size.width / 48f
        while (x < size.width) {
            hash = hash * 1103515245 + 12345 // a tiny pseudo-random sequence
            val bar = (((hash ushr 16) and 3) + 1) * unit * 0.6f
            val gap = (((hash ushr 20) and 1) + 1) * unit
            drawRect(color, topLeft = Offset(x, 0f), size = Size(bar.coerceAtMost(size.width - x), size.height))
            x += bar + gap
        }
    }
}

/**
 * The north-east arrow, designed in Figma: design/arrow.svg in the repo.
 * Its path data is copied here as an ImageVector (built once, then reused).
 * To change the arrow: redesign it, export SVG, and replace the "d" text below.
 */
private val ArrowVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "Arrow",
        defaultWidth = 40.dp,
        defaultHeight = 40.dp,
        viewportWidth = 96f,   // the Figma frame was 96 × 96
        viewportHeight = 96f,
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M89 6.35645L89.0352 6.35742L89 14.8027V27H88.9492L88.6885 89.6416L67.6885 89.5537L67.8809 43.376" +
                    "L20.8496 90.4082L6 75.5596L54.5596 27H6V6H89V6.35645Z"
            ),
            fill = SolidColor(Color.Black), // recoloured by `tint` when drawn
        )
    }.build()
}

/** The big arrow decoration. Takes the colour of the text around it unless you pass one. */
@Composable
fun BigArrow(modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Icon(ArrowVector, contentDescription = null, tint = color, modifier = modifier.size(40.dp))
}

/**
 * A tiny spec-sheet footer: a code on the left, a barcode, and a stamp on the right.
 * e.g. "#E9A25A  |||| | |||  NXT/09.11"
 */
@Composable
fun SpecFooter(left: String, right: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(left, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Barcode(seed = left + right)
        Spacer(Modifier.width(10.dp))
        Text(right, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * A big number in an outlined box with a mono label under it, like the reference's "25".
 * [numberColor] lets each box use its page's colour.
 */
@Composable
fun StatBox(number: Int, label: String, numberColor: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            number.toString().padStart(2, '0'),
            style = MaterialTheme.typography.displaySmall,
            color = numberColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "#E9A25A": a colour written as a hex code, for the spec-sheet labels. */
fun hexCode(color: Color): String =
    "#" + (color.toArgb() and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')
