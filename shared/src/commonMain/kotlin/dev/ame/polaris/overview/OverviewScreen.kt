package dev.ame.polaris.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ame.polaris.Page
import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.calendar.EventDao
import dev.ame.polaris.data.SettingDao
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.schedule.ScheduleDao
import dev.ame.polaris.schedule.isHappeningAt
import dev.ame.polaris.tasks.Task
import dev.ame.polaris.tasks.TaskDao
import dev.ame.polaris.ui.rememberCurrentTime
import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.longDate
import dev.ame.polaris.weather.WeatherBadge
import dev.ame.polaris.weather.WeatherSettingsDialog
import dev.ame.polaris.weather.WeatherUiState
import dev.ame.polaris.weather.WeatherViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import dev.ame.polaris.ui.PolarisCard

/** Most rows a card shows before saying "+ N more". */
private const val MaxRows = 6

@Composable
fun OverviewScreen(
    eventDao: EventDao,
    taskDao: TaskDao,
    scheduleDao: ScheduleDao,
    settingDao: SettingDao,
    onOpen: (Page) -> Unit,
    modifier: Modifier = Modifier,
    vm: OverviewViewModel = viewModel { OverviewViewModel(eventDao, taskDao, scheduleDao) },
    weatherVm: WeatherViewModel = viewModel { WeatherViewModel(settingDao) },
) {
    val state by vm.state.collectAsState()
    val weather by weatherVm.state.collectAsState()
    val now by rememberCurrentTime()
    var weatherDialog by remember { mutableStateOf(false) }

    // Runs on open and again each time `now` ticks (every 30 s). It only actually
    // fetches when the saved weather is over 30 minutes old.
    LaunchedEffect(now) { weatherVm.refreshIfStale() }
    val events = upcomingEvents(state.events, state.today, now)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(state.today, now, weather, onWeatherClick = { weatherDialog = true })
            NowAndNextCard(nowAndNext(state.blocks, now))

            val schedule: @Composable (Modifier) -> Unit = { m ->
                ScheduleCard(state.blocks, now, onSeeAll = { onOpen(Page.Schedule) }, modifier = m)
            }
            val upcoming: @Composable (Modifier) -> Unit = { m ->
                EventsCard(events, state.today, onSeeAll = { onOpen(Page.Calendar) }, modifier = m)
            }
            val tasks: @Composable (Modifier) -> Unit = { m ->
                TasksCard(state.tasks, state.today, onComplete = vm::completeTask, onSeeAll = { onOpen(Page.Tasks) }, modifier = m)
            }

            if (wide) {
                // Two columns: the day on the left, what's coming on the right
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    schedule(Modifier.weight(1f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        upcoming(Modifier.fillMaxWidth())
                        tasks(Modifier.fillMaxWidth())
                    }
                }
            } else {
                schedule(Modifier.fillMaxWidth())
                upcoming(Modifier.fillMaxWidth())
                tasks(Modifier.fillMaxWidth())
            }
        }
    }

    if (weatherDialog) {
        WeatherSettingsDialog(
            state = weather,
            onSearch = weatherVm::search,
            onChoose = weatherVm::choosePlace,
            onUnit = weatherVm::setUnit,
            onDismiss = { weatherDialog = false },
        )
    }
}

// ---------- Header ----------

@Composable
private fun Header(today: LocalDate, now: LocalTime, weather: WeatherUiState, onWeatherClick: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(longDate(today), color = MaterialTheme.colorScheme.primary)
            // Reads `now`, so the greeting changes by itself at 12:00 and 18:00.
            Text(
                "${greetingFor(now.hour)}, Ame",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        WeatherBadge(weather, onClick = onWeatherClick)
    }
}

@Composable
private fun NowAndNextCard(nn: NowAndNext) {
    if (nn.now == null && nn.next == null) return
    PolarisCard(Modifier.fillMaxWidth(), highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nn.now?.let {
                LabeledLine("Now", "${it.title} · until ${hhmm(it.end)}", highlight = true)
            }
            nn.next?.let {
                LabeledLine("Next", "${it.title} · ${hhmm(it.start)}", highlight = false)
            }
        }
    }
}

@Composable
private fun LabeledLine(label: String, text: String, highlight: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) colors.primary else colors.onSurfaceVariant,
        )
        Text(
            text,
            color = colors.onSurface,
            fontWeight = if (highlight) FontWeight.SemiBold else null,
        )
    }
}

// ---------- Cards ----------

/** A titled card with a "See all ›" link to the full page. */
@Composable
private fun GlanceCard(
    title: String,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PolarisCard(modifier) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onSeeAll) { Text("See all ›") }
            }
            Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun MoreLine(hidden: Int) {
    if (hidden > 0) {
        Text("+ $hidden more", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

/** Time on the left (fixed width so titles line up), then the text. */
@Composable
private fun TimedRow(time: String, title: String, timeHighlight: Boolean = false, dim: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row {
        Text(
            time,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (timeHighlight) colors.primary else colors.onSurfaceVariant,
        )
        Text(title, color = if (dim) colors.onSurfaceVariant else colors.onSurface)
    }
}

@Composable
private fun ScheduleCard(blocks: List<ScheduleBlock>, now: LocalTime, onSeeAll: () -> Unit, modifier: Modifier) {
    GlanceCard("Today's schedule", onSeeAll, modifier) {
        if (blocks.isEmpty()) EmptyLine("Nothing scheduled today.")
        blocks.forEach { block ->
            TimedRow(
                time = "${hhmm(block.start)}–${hhmm(block.end)}",
                title = block.title,
                timeHighlight = block.isHappeningAt(now),
                dim = block.end <= now, // already over
            )
        }
    }
}

@Composable
private fun EventsCard(events: List<CalendarEvent>, today: LocalDate, onSeeAll: () -> Unit, modifier: Modifier) {
    GlanceCard("Upcoming events", onSeeAll, modifier) {
        if (events.isEmpty()) EmptyLine("No events in the next $UpcomingDays days.")
        events.take(MaxRows).forEach { event ->
            val day = dayLabel(event.date, today)
            val time = event.start?.let(::hhmm) ?: "All day"
            TimedRow(time = "$day · $time", title = event.title, timeHighlight = event.date == today)
        }
        MoreLine(events.size - MaxRows)
    }
}

@Composable
private fun TasksCard(
    tasks: List<Task>,
    today: LocalDate,
    onComplete: (String) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    GlanceCard("Tasks due", onSeeAll, modifier) {
        if (tasks.isEmpty()) EmptyLine("Nothing due this week.")
        tasks.take(MaxRows).forEach { task ->
            val due = task.due ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ticking a task completes it; it then drops off this list.
                Checkbox(
                    checked = false,
                    onCheckedChange = { onComplete(task.id) },
                    colors = CheckboxDefaults.colors(uncheckedColor = colors.onSurfaceVariant),
                )
                Text(task.title, modifier = Modifier.weight(1f), color = colors.onSurface)
                Text(
                    if (due < today) "Overdue" else dayLabel(due, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        due < today -> colors.error
                        due == today -> colors.primary
                        else -> colors.onSurfaceVariant
                    },
                )
            }
        }
        MoreLine(tasks.size - MaxRows)
    }
}
