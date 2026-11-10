package dev.ame.polaris.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ame.polaris.schedule.oneHourLater
import dev.ame.polaris.tasks.Task
import dev.ame.polaris.tasks.TaskDao
import dev.ame.polaris.ui.DateArrows
import dev.ame.polaris.ui.LocalUndo
import dev.ame.polaris.ui.PageHeader
import dev.ame.polaris.ui.PagePadding
import dev.ame.polaris.ui.PolarisIcons
import dev.ame.polaris.ui.QuietIconButton
import dev.ame.polaris.ui.TimeButton
import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.longDate
import dev.ame.polaris.util.shortDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import dev.ame.polaris.ui.Gold
import dev.ame.polaris.ui.GoldButton
import dev.ame.polaris.ui.PolarisCard
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
fun CalendarScreen(
    eventDao: EventDao,
    taskDao: TaskDao,
    modifier: Modifier = Modifier,
    vm: CalendarViewModel = viewModel { CalendarViewModel(eventDao, taskDao) },
) {
    val state by vm.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    val undo = LocalUndo.current

    BoxWithConstraints(modifier.fillMaxSize().padding(PagePadding)) {
        val wide = maxWidth >= 720.dp
        val month: @Composable (Modifier) -> Unit = { m ->
            MonthView(
                state = state,
                onPrevious = vm::previousMonth,
                onNext = vm::nextMonth,
                onToday = vm::goToToday,
                onSelect = vm::select,
                // Taller cells with more titles when there's room
                cellHeight = if (wide) 100.dp else 64.dp,
                maxChips = if (wide) 3 else 2,
                modifier = m,
            )
        }
        val day: @Composable (Modifier) -> Unit = { m ->
            DayPanel(
                state = state,
                onAdd = { adding = true },
                onEditEvent = { editing = it },
                onDeleteEvent = { event ->
                    vm.deleteEvent(event.id)
                    undo.offer("Event deleted") { vm.restoreEvent(event) }
                },
                onToggleTask = vm::toggleTask,
                modifier = m,
            )
        }

        if (wide) {
            // Wide: the month fills the page, the selected day sits on the right
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                month(Modifier.weight(1f).verticalScroll(rememberScrollState()))
                day(Modifier.width(320.dp))
            }
        } else {
            // Narrow: month on top, the selected day below
            Column(Modifier.fillMaxSize()) {
                month(Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                day(Modifier.weight(1f))
            }
        }
    }

    if (adding || editing != null) {
        val existing = editing
        EventDialog(
            existing = existing,
            date = state.selected,
            onDismiss = { adding = false; editing = null },
            onSave = { title, date, start, end, endDate ->
                vm.saveEvent(existing, title, date, start, end, endDate)
                adding = false
                editing = null
            },
            onDelete = existing?.let { event ->
                {
                    editing = null
                    vm.deleteEvent(event.id)
                    undo.offer("Event deleted") { vm.restoreEvent(event) }
                }
            },
        )
    }
}

// ---------- Month grid ----------

@Composable
private fun MonthView(
    state: CalendarUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    cellHeight: Dp,
    maxChips: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        PageHeader("Calendar", subtitle = monthTitle(state.month)) {
            DateArrows(onToday = onToday, onPrevious = onPrevious, onNext = onNext)
        }
        Spacer(Modifier.height(16.dp))

        Row {
            WeekdayLabels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // 42 days -> 6 rows of 7. Multi-day events are drawn as bars across each row,
        // on top of the cells; each cell leaves room for them above its own chips.
        val allEvents = remember(state.events) { state.events.values.flatten().distinctBy { it.id } }
        state.days.chunked(7).forEach { week ->
            val bars = remember(week, allEvents) { weekBars(week, allEvents) }
            val lanes = minOf((bars.maxOfOrNull { it.lane } ?: -1) + 1, maxChips)
            Box(Modifier.fillMaxWidth().height(cellHeight)) {
                Row {
                    week.forEachIndexed { col, date ->
                        // Bars that didn't fit on this day still count towards its "+N"
                        val hiddenBars = bars.count { it.lane >= lanes && col in it.startCol..it.endCol }
                        DayCell(
                            date = date,
                            inMonth = date.month == state.month.month,
                            isToday = date == state.today,
                            isSelected = date == state.selected,
                            chips = dayChips(state.events[date].orEmpty(), state.tasks[date].orEmpty()),
                            maxChips = maxChips,
                            barLanes = lanes,
                            hiddenBars = hiddenBars,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
                WeekBarsOverlay(bars.filter { it.lane < lanes }, week, onSelect)
            }
        }
    }
}

/**
 * One day in the month grid, Google Calendar style: the date at the top (today in a gold
 * circle), then the day's events and tasks as small title chips, then "+N" if they don't fit.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    chips: List<DayChip>,
    maxChips: Int,
    barLanes: Int,
    hiddenBars: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .padding(1.dp)
            .clip(shape)
            .background(colors.surface.copy(alpha = if (isSelected) 0.75f else 0.22f))
            .then(if (isSelected) Modifier.border(1.dp, Gold.Edge, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .alpha(if (inMonth) 1f else 0.4f), // days from the next/previous month fade back
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(22.dp)
                .clip(CircleShape)
                .then(if (isToday) Modifier.background(Gold.Shine) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else null,
                color = if (isToday) colors.onPrimary else colors.onBackground,
            )
        }
        // Room for the week's multi-day bars (drawn over the cells by WeekBarsOverlay)
        if (barLanes > 0) Spacer(Modifier.height(ChipSlot * barLanes - ChipGap))
        // The rest is for this day's own chips; if they don't all fit, keep one slot for "+N"
        val room = (maxChips - barLanes).coerceAtLeast(0)
        val shown = if (chips.size > room) chips.take((room - 1).coerceAtLeast(0)) else chips
        shown.forEach { DayChipLabel(it) }
        val hidden = chips.size - shown.size + hiddenBars
        if (hidden > 0) {
            Text(
                "+$hidden",
                modifier = Modifier.padding(start = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** What a cell shows for one event or task. */
private data class DayChip(val title: String, val isEvent: Boolean, val done: Boolean = false)

/**
 * All-day events first, then timed events by start time, then tasks due that day.
 * (Multi-day events aren't chips: they're bars across the week.)
 */
private fun dayChips(events: List<CalendarEvent>, tasks: List<Task>): List<DayChip> =
    events.filter { !it.isMultiDay }.sortedWith(compareBy(nullsFirst()) { it.start }).map { DayChip(it.title, isEvent = true) } +
        tasks.map { DayChip(it.title, isEvent = false, done = it.done) }

/** Events: a gold-tinted chip. Tasks: a grey one, crossed out when done. */
@Composable
private fun DayChipLabel(chip: DayChip) {
    val colors = MaterialTheme.colorScheme
    Text(
        chip.title,
        modifier = Modifier
            .fillMaxWidth()
            .height(ChipHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(if (chip.isEvent) colors.primary.copy(alpha = 0.30f) else colors.onSurfaceVariant.copy(alpha = 0.16f))
            .padding(horizontal = 3.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall,
        color = if (chip.done) colors.onSurfaceVariant else colors.onSurface,
        textDecoration = if (chip.done) TextDecoration.LineThrough else null,
        maxLines = 1,
        overflow = TextOverflow.Clip,
    )
}


// ---------- Selected day ----------

@Composable
private fun DayPanel(
    state: CalendarUiState,
    onAdd: () -> Unit,
    onDeleteEvent: (CalendarEvent) -> Unit,
    onEditEvent: (CalendarEvent) -> Unit,
    onToggleTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                longDate(state.selected),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            GoldButton(onClick = onAdd) {
                Icon(PolarisIcons.Add, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Add event")
            }
        }
        Spacer(Modifier.height(12.dp))

        if (state.selectedEvents.isEmpty() && state.selectedTasks.isEmpty()) {
            Text(
                "Nothing planned. Add an event, or give a task this due date.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Prefixed keys: an event and a task can never be confused for each other.
            items(state.selectedEvents, key = { "e-${it.id}" }) { event ->
                EventRow(event, onClick = { onEditEvent(event) }, onDelete = { onDeleteEvent(event) }, Modifier.animateItem())
            }
            items(state.selectedTasks, key = { "t-${it.id}" }) { task ->
                DueTaskRow(task, onToggle = { onToggleTask(task.id) }, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    PolarisCard(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                timeLabel(event),
                modifier = Modifier.width(if (event.isMultiDay) 132.dp else 96.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
            )
            Text(event.title, modifier = Modifier.weight(1f), color = colors.onSurface)
            QuietIconButton(PolarisIcons.Delete, "Delete event", onClick = onDelete)
        }
    }
}

/** A task due this day. Ticking it here is the same as ticking it on the Tasks page. */
@Composable
private fun DueTaskRow(task: Task, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    PolarisCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.primary,
                    checkmarkColor = colors.onPrimary,
                    uncheckedColor = colors.onSurfaceVariant,
                ),
            )
            Text(
                task.title,
                modifier = Modifier.weight(1f),
                color = if (task.done) colors.onSurfaceVariant else colors.onSurface,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            Text(
                "Task",
                modifier = Modifier.padding(end = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

private fun timeLabel(event: CalendarEvent): String {
    if (event.isMultiDay) {
        // "9–11 Nov", or with times "9 Nov 14:00 → 11 Nov 10:00"
        val from = shortDate(event.date) + (event.start?.let { " ${hhmm(it)}" } ?: "")
        val to = shortDate(event.lastDate) + (event.end?.let { " ${hhmm(it)}" } ?: "")
        return "$from → $to"
    }
    val start = event.start ?: return "All day"
    val end = event.end ?: return hhmm(start)
    return "${hhmm(start)}–${hhmm(end)}"
}

// ---------- Multi-day bars ----------

// Cell geometry, so the bars line up with the chip rows inside each cell
private val ChipHeight = 16.dp
private val ChipGap = 2.dp
private val ChipSlot = ChipHeight + ChipGap
/** From the top of a cell to its first chip row: outer padding + top padding + date circle + gap. */
private val FirstChipTop = 1.dp + 3.dp + 22.dp + ChipGap

/**
 * Draws one week row's multi-day events as continuous bars, Google Calendar style.
 * Rounded ends only where the event really starts/ends; square where it continues
 * from the previous week or into the next. Tapping a bar selects its first day here.
 */
@Composable
private fun WeekBarsOverlay(bars: List<WeekBar>, week: List<LocalDate>, onSelect: (LocalDate) -> Unit) {
    if (bars.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val col = maxWidth / 7
        bars.forEach { bar ->
            val round = 4.dp
            val shape = RoundedCornerShape(
                topStart = if (bar.continuesBefore) 0.dp else round,
                bottomStart = if (bar.continuesBefore) 0.dp else round,
                topEnd = if (bar.continuesAfter) 0.dp else round,
                bottomEnd = if (bar.continuesAfter) 0.dp else round,
            )
            // Inset a little where the bar really starts/ends, to line up with the cells' chips
            val startInset = if (bar.continuesBefore) 0.dp else 3.dp
            val endInset = if (bar.continuesAfter) 0.dp else 3.dp
            Text(
                bar.event.title,
                modifier = Modifier
                    .offset(x = col * bar.startCol + startInset, y = FirstChipTop + ChipSlot * bar.lane)
                    .width(col * bar.span - startInset - endInset)
                    .height(ChipHeight)
                    .clip(shape)
                    .background(colors.primary.copy(alpha = 0.55f))
                    .clickable { onSelect(week[bar.startCol]) }
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
