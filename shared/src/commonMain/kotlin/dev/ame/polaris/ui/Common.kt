package dev.ame.polaris.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ame.polaris.LocalPage
import dev.ame.polaris.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Small pieces every page uses, so all pages look and behave the same.

/** Space around each page's content. */
val PagePadding = 24.dp

/** Lists and text don't stretch wider than this on big screens; long lines are tiring to read. */
val ReadableWidth = 720.dp

/**
 * The top of a page: a big title, an optional gold subtitle (e.g. the date), and
 * buttons on the right.
 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // A solid panel in the page's colour, spec-sheet style: a small "02" in the corner,
    // a dot, then the title in big wide capitals and the subtitle as a mono caption.
    val page = LocalPage.current
    PolarisCard(modifier.fillMaxWidth(), color = page.color) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(page.number, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(7.dp).clip(CircleShape)
                        .background(if (page.color == Palette.Terracotta) Palette.Ink else Palette.Terracotta)
                )
                Spacer(Modifier.weight(1f))
                // The panel's colour as a hex code, like "#EOA15E" in the reference
                Text(hexCode(page.color), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium)
                    if (subtitle != null) {
                        Text(subtitle.uppercase(), style = MaterialTheme.typography.labelMedium)
                    }
                }
                actions()
            }
        }
    }
}

/** "Today ‹ ›" for moving between days, weeks or months. */
@Composable
fun DateArrows(onToday: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onToday) { Text("Today") }
        IconButton(onClick = onPrevious) { Icon(PolarisIcons.ChevronLeft, contentDescription = "Previous") }
        IconButton(onClick = onNext) { Icon(PolarisIcons.ChevronRight, contentDescription = "Next") }
    }
}

/** A small icon button in the quiet grey used for secondary actions like delete. */
@Composable
fun QuietIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What a page shows when it has nothing yet: an icon, a line saying so, and a hint what to do. */
@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

// ---------- Undo ----------

/**
 * Shows "Task deleted · Undo" at the bottom of the screen. Deleting happens right away
 * (no "are you sure?" dialogs), and Undo puts the item back.
 */
class UndoController(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun offer(message: String, undo: () -> Unit) {
        scope.launch {
            host.currentSnackbarData?.dismiss() // only one at a time
            val result = host.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }
}

/**
 * Lets any screen reach the app's UndoController without passing it through every function.
 * App() provides it; screens use `LocalUndo.current.offer(...)`.
 */
val LocalUndo = staticCompositionLocalOf<UndoController> { error("UndoController not provided") }

/** Where the Undo bar appears, styled to match the app. */
@Composable
fun PolarisSnackbarHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(host, modifier) { data ->
        Snackbar(
            data,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.medium,
        )
    }
}

/** Arranges items with the standard gap between them. */
val ItemGap = Arrangement.spacedBy(8.dp)
