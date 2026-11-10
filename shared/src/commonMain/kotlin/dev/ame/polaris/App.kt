package dev.ame.polaris

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ame.polaris.calendar.CalendarScreen
import dev.ame.polaris.data.PolarisDatabase
import dev.ame.polaris.notes.NotesScreen
import dev.ame.polaris.overview.OverviewScreen
import dev.ame.polaris.schedule.ScheduleScreen
import dev.ame.polaris.sync.LocalSync
import dev.ame.polaris.sync.SyncManager
import dev.ame.polaris.tasks.TasksScreen
import dev.ame.polaris.ui.LocalUndo
import dev.ame.polaris.ui.PolarisSnackbarHost
import dev.ame.polaris.ui.UndoController

/**
 * The whole app. Each platform opens the database and passes it in, plus its cloud
 * sync (null = no sync, e.g. in previews).
 */
@Composable
fun App(database: PolarisDatabase, sync: SyncManager? = null) {
    PolarisTheme {
        // Which tab is showing. Changing it redraws the parts of the UI that read it.
        var current by rememberSaveable { mutableStateOf(Page.Overview) }

        // The "Undo" bar, shared by every page
        val snackbar = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val undo = remember { UndoController(snackbar, scope) }

        CompositionLocalProvider(LocalUndo provides undo, LocalSync provides sync) {
            BoxWithConstraints(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                if (maxWidth >= 600.dp) {
                    // Wide (desktop, tablet): rail on the left
                    Box(Modifier.fillMaxSize().safeContentPadding()) {
                        Row(Modifier.fillMaxSize()) {
                            NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                                Page.entries.forEach { page ->
                                    NavigationRailItem(
                                        selected = page == current,
                                        onClick = { current = page },
                                        icon = { NavIcon(page, page == current) },
                                        label = { Text(page.label) },
                                        colors = NavigationRailItemDefaults.colors(
                                            indicatorColor = Color.Transparent, // we draw our own shiny one
                                            selectedTextColor = page.color,
                                        ),
                                    )
                                }
                            }
                            PageContent(current, database, onOpen = { current = it }, Modifier.weight(1f))
                        }
                        PolarisSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
                    }
                } else {
                    // Narrow (phone): bar at the bottom
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { PolarisSnackbarHost(snackbar) },
                        bottomBar = {
                            NavigationBar {
                                Page.entries.forEach { page ->
                                    NavigationBarItem(
                                        selected = page == current,
                                        onClick = { current = page },
                                        icon = { NavIcon(page, page == current) },
                                        label = { Text(page.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = Color.Transparent, // we draw our own shiny one
                                            selectedTextColor = page.color,
                                        ),
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        PageContent(current, database, onOpen = { current = it }, Modifier.padding(padding))
                    }
                }
            }
        }
    }
}

/** A tab's icon; the selected one sits on a pill in that page's own colour. */
@Composable
private fun NavIcon(page: Page, selected: Boolean) {
    if (selected) {
        Box(
            Modifier.size(width = 56.dp, height = 32.dp).clip(RoundedCornerShape(16.dp)).background(page.color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(page.icon, contentDescription = null, tint = Palette.Ink)
        }
    } else {
        Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PageContent(
    page: Page,
    database: PolarisDatabase,
    onOpen: (Page) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A quick cross-fade when switching pages, instead of a hard cut.
    AnimatedContent(
        targetState = page,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        label = "page",
    ) { shown ->
        PageTheme(shown) {
            // No `else`: Kotlin forces every Page to be handled.
            when (shown) {
                Page.Overview -> OverviewScreen(
                    database.eventDao(), database.taskDao(), database.scheduleDao(), database.settingDao(),
                    onOpen = onOpen,
                )
                Page.Calendar -> CalendarScreen(database.eventDao(), database.taskDao())
                Page.Tasks -> TasksScreen(database.taskDao())
                Page.Schedule -> ScheduleScreen(database.scheduleDao(), database.eventDao())
                Page.Notes -> NotesScreen(database.noteDao())
            }
        }
    }
}

/**
 * Gives a page its own accent: buttons, checkboxes, highlights and "today" all take the
 * page's colour (sage on Tasks, terracotta on Calendar…). Also tells shared pieces which
 * page they're on (LocalPage), e.g. for the "02" in the header.
 */
@Composable
private fun PageTheme(page: Page, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme.copy(
        primary = page.color,
        secondary = page.color,
        secondaryContainer = page.color,
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
        CompositionLocalProvider(LocalPage provides page, content = content)
    }
}
