package dev.ame.polaris

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ame.polaris.calendar.CalendarScreen
import dev.ame.polaris.data.PolarisDatabase
import dev.ame.polaris.notes.NotesScreen
import dev.ame.polaris.overview.OverviewScreen
import dev.ame.polaris.schedule.ScheduleScreen
import dev.ame.polaris.tasks.TasksScreen

/** The whole app. Each platform opens the database and passes it in. */
@Composable
fun App(database: PolarisDatabase) {
    PolarisTheme {
        // Which tab is showing. Changing it redraws the parts of the UI that read it.
        var current by rememberSaveable { mutableStateOf(Page.Overview) }

        BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        ) {
            if (maxWidth >= 600.dp) {
                // Wide (desktop, tablet): rail on the left
                Row(Modifier.fillMaxSize().safeContentPadding()) {
                    NavigationRail {
                        Page.entries.forEach { page ->
                            NavigationRailItem(
                                selected = page == current,
                                onClick = { current = page },
                                icon = { Text(page.symbol) },
                                label = { Text(page.label) },
                            )
                        }
                    }
                    PageContent(current, database, onOpen = { current = it }, Modifier.weight(1f))
                }
            } else {
                // Narrow (phone): bar at the bottom
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            Page.entries.forEach { page ->
                                NavigationBarItem(
                                    selected = page == current,
                                    onClick = { current = page },
                                    icon = { Text(page.symbol) },
                                    label = { Text(page.label) },
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

@Composable
private fun PageContent(
    page: Page,
    database: PolarisDatabase,
    onOpen: (Page) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No `else`: Kotlin forces every Page to be handled.
    when (page) {
        Page.Overview -> OverviewScreen(
            database.eventDao(), database.taskDao(), database.scheduleDao(), database.settingDao(),
            onOpen = onOpen,
            modifier = modifier,
        )
        Page.Calendar -> CalendarScreen(database.eventDao(), database.taskDao(), modifier)
        Page.Tasks -> TasksScreen(database.taskDao(), modifier)
        Page.Schedule -> ScheduleScreen(database.scheduleDao(), modifier)
        Page.Notes -> NotesScreen(database.noteDao(), modifier)
    }
}
