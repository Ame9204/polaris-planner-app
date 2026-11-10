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
import dev.ame.polaris.ui.Gold
import dev.ame.polaris.ui.CardAlpha
import dev.ame.polaris.schedule.layoutLanes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import dev.ame.polaris.ui.Panel
import kotlinx.datetime.number
import dev.ame.polaris.ui.hexCode
import dev.ame.polaris.ui.StatBox
import dev.ame.polaris.ui.SpecFooter
import dev.ame.polaris.ui.BigArrow
import dev.ame.polaris.sync.SyncBadge
import dev.ame.polaris.sync.AccountDialog


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
    var accountDialog by remember { mutableStateOf(false) }

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
            Header(state.today, now, weather, onWeatherClick = { weatherDialog = true }, onSyncClick = { accountDialog = true })
            StatsRow(todayStats(state.dueTasks, state.todayEvents, state.blocks, state.today, now))
            NowAndNextCard(nowAndNext(state.blocks, now), now, state.today)

            val schedule: @Composable (Modifier) -> Unit = { m ->
                ScheduleCard(state.blocks, now, onSeeAll = { onOpen(Page.Schedule) }, modifier = m)
            }
            val upcoming: @Composable (Modifier) -> Unit = { m ->
                EventsCard(events, state.today, now, onSeeAll = { onOpen(Page.Calendar) }, modifier = m)
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

    if (accountDialog) {
        AccountDialog(onDismiss = { accountDialog = false })
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
private fun Header(
    today: LocalDate,
    now: LocalTime,
    weather: WeatherUiState,
    onWeatherClick: () -> Unit,
    onSyncClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(longDate(today).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            // Reads `now`, so the greeting changes by itself at 12:00 and 18:00.
            Text(
                greetingFor(now.hour, today).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        // Weather, and under it the sync status (tap either to change things)
        Column(horizontalAlignment = Alignment.End) {
            WeatherBadge(weather, onClick = onWeatherClick)
            SyncBadge(onClick = onSyncClick)
        }
    }
}

/** Three big numbers in outlined boxes: tasks due, events today, blocks left. */
@Composable
private fun StatsRow(stats: TodayStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Same order and colours as the cards below: schedule (sage), events (terracotta), tasks (stone)
        StatBox(stats.blocksLeft, "Blocks left", Panel.Sage, Modifier.weight(1f))
        StatBox(stats.eventsToday, "Events today", Panel.Terracotta, Modifier.weight(1f))
        StatBox(stats.tasksDue, "Tasks due", Panel.Stone, Modifier.weight(1f))
    }
}

/**
 * Always shown. What's on now (or "free time"), what's next, or, when nothing else is
 * on today, a fun line instead. Decorated like a spec sheet: big arrow, barcode footer.
 */
@Composable
private fun NowAndNextCard(nn: NowAndNext, now: LocalTime, today: LocalDate) {
    PolarisCard(Modifier.fillMaxWidth(), color = Panel.Tangerine) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Same heading style as the other cards ("TODAY'S SCHEDULE", "UPCOMING EVENTS")
                Text(
                    "NOW / NEXT",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                BigArrow()
            }

            val current = nn.now
            val next = nn.next
            if (current != null) {
                LabeledLine("Now", current.title, "until ${hhmm(current.end)}", countdown = timeLeft(now, current.end), highlight = true)
            } else if (next != null) {
                LabeledLine("Now", "Free time", "until ${hhmm(next.start)}", countdown = "", highlight = false)
            }

            if (next != null) {
                val minutes = ((next.start.toSecondOfDay() - now.toSecondOfDay()) / 60).toLong()
                LabeledLine("Next", next.title, "at ${hhmm(next.start)}", countdown = countdown(minutes), highlight = current == null)
            } else if (current == null) {
                // Nothing now and nothing next: a fun line in big type, changing every hour.
                // (If something's on now, that's enough on its own.)
                Text(idleLine(today, now.hour).uppercase(), style = MaterialTheme.typography.headlineSmall)
            }

            SpecFooter(left = hexCode(Panel.Tangerine), right = "NXT/${today.day.toString().padStart(2, '0')}.${today.month.number.toString().padStart(2, '0')}")
        }
    }
}

/** "Now  Work · until 17:00            1 h 20 min left" */
@Composable
private fun LabeledLine(label: String, title: String, detail: String, countdown: String, highlight: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            modifier = Modifier.width(52.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) colors.onSurface else colors.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onSurface, fontWeight = if (highlight) FontWeight.SemiBold else null)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        // The countdown, big enough to read at a glance
        Text(
            countdown.uppercase(),
            style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
        )
    }
}

// ---------- Cards ----------

/** A titled card with a "See all ›" link to the full page. */
@Composable
private fun GlanceCard(
    title: String,
    color: Color,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    PolarisCard(modifier, color = color) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.uppercase(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onSeeAll) { Text("SEE ALL ↗") }
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

/** Time on the left (fixed width so titles line up), then the text. */
@Composable
private fun TimedRow(
    time: String,
    title: String,
    timeHighlight: Boolean = false,
    dim: Boolean = false,
    trailing: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            time,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (timeHighlight) colors.primary else colors.onSurfaceVariant,
        )
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (dim) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
    }
}

@Composable
private fun ScheduleCard(blocks: List<ScheduleBlock>, now: LocalTime, onSeeAll: () -> Unit, modifier: Modifier) {
    GlanceCard("Today's schedule", Panel.Sage, onSeeAll, modifier) {
        if (blocks.isEmpty()) {
            EmptyLine("Nothing scheduled today.")
        } else {
            ScheduleBar(blocks, now)
            Spacer(Modifier.height(4.dp))
        }
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
private fun EventsCard(events: List<CalendarEvent>, today: LocalDate, now: LocalTime, onSeeAll: () -> Unit, modifier: Modifier) {
    GlanceCard("Upcoming events", Panel.Terracotta, onSeeAll, modifier) {
        if (events.isEmpty()) EmptyLine("No upcoming events.")
        events.take(GlanceCount).forEach { event ->
            // Ongoing multi-day events count as today
            val day = if (event.date < today) "Today" else dayLabel(event.date, today)
            val time = event.start?.let(::hhmm) ?: "All day"
            TimedRow(
                time = "$day · $time",
                title = event.title,
                timeHighlight = event.occursOn(today),
                trailing = eventCountdown(event, today, now),
            )
        }
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
    GlanceCard("Tasks due", Panel.Stone, onSeeAll, modifier) {
        // Read the colours *inside* the panel, so they're the panel's ink, not the page's white
        val colors = MaterialTheme.colorScheme
        if (tasks.isEmpty()) EmptyLine("Nothing due.")
        tasks.take(GlanceCount).forEach { task ->
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
                    dueCountdown(due, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        due < today -> colors.error
                        due == today -> colors.primary
                        else -> colors.onSurfaceVariant
                    },
                )
            }
        }
    }
}

// ---------- Today's schedule as a bar ----------

/**
 * The day as one horizontal bar: each block is a segment placed by its real times, so
 * you see the shape of the day at a glance. Past blocks are faded, the current one is
 * shiny gold, and a gold line marks now. Overlapping blocks stack in thinner rows.
 */
@Composable
private fun ScheduleBar(blocks: List<ScheduleBlock>, now: LocalTime) {
    val colors = MaterialTheme.colorScheme
    val hours = remember(blocks) { barHours(blocks) }
    val placed = remember(blocks) { layoutLanes(blocks) }
    val barHeight = 36.dp
    val shape = RoundedCornerShape(8.dp)

    Column {
        BoxWithConstraints(
            Modifier.fillMaxWidth().height(barHeight).clip(shape).background(colors.onSurface.copy(alpha = 0.10f)),
        ) {
            val width = maxWidth

            // Faint line every hour, so slots line up with the labels below
            for (h in hours.first + 1 until hours.second) {
                val x = width * barFraction(LocalTime(h, 0), hours)
                Box(Modifier.offset(x = x).width(1.dp).fillMaxHeight().background(colors.onSurface.copy(alpha = 0.12f)))
            }

            placed.forEach { p ->
                val b = p.block
                val x = width * barFraction(b.start, hours)
                val w = (width * barFraction(b.end, hours) - x).coerceAtLeast(3.dp)
                val segmentHeight = barHeight / p.laneCount
                val isNow = b.isHappeningAt(now)
                val isPast = b.end <= now
                Box(
                    Modifier
                        .offset(x = x, y = segmentHeight * p.lane)
                        .width(w)
                        .height(segmentHeight)
                        .padding(1.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(
                            when {
                                isNow -> Modifier.background(Gold.Shine)
                                isPast -> Modifier.background(colors.onSurfaceVariant.copy(alpha = 0.18f))
                                else -> Modifier.background(colors.primary.copy(alpha = 0.28f))
                            }
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Only label segments wide enough to fit a word
                    if (w >= 44.dp && segmentHeight >= 18.dp) {
                        Text(
                            b.title,
                            modifier = Modifier.padding(horizontal = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isNow) colors.onPrimary else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }

            // Now marker
            val nowX = width * barFraction(now, hours)
            if (now.hour >= hours.first && now.hour < hours.second) {
                Box(Modifier.offset(x = nowX - 1.dp).width(2.dp).fillMaxHeight().background(colors.onBackground))
            }
        }

        // Hour labels every 3 hours under the bar
        BoxWithConstraints(Modifier.fillMaxWidth().height(16.dp)) {
            val width = maxWidth
            for (h in hours.first..hours.second step 3) {
                val x = width * barFraction(LocalTime(minOf(h, 23), if (h == 24) 59 else 0), hours)
                Text(
                    h.toString().padStart(2, '0'),
                    modifier = Modifier.offset(x = (x - 8.dp).coerceIn(0.dp, width - 16.dp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
