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
import dev.ame.polaris.ui.TimeButton
import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.longDate
import dev.ame.polaris.util.shortDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Composable
fun CalendarScreen(
    eventDao: EventDao,
    taskDao: TaskDao,
    modifier: Modifier = Modifier,
    vm: CalendarViewModel = viewModel { CalendarViewModel(eventDao, taskDao) },
) {
    val state by vm.state.collectAsState()
    var adding by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().padding(24.dp)) {
        val month: @Composable (Modifier) -> Unit = { m ->
            MonthView(
                state = state,
                onPrevious = vm::previousMonth,
                onNext = vm::nextMonth,
                onToday = vm::goToToday,
                onSelect = vm::select,
                modifier = m,
            )
        }
        val day: @Composable (Modifier) -> Unit = { m ->
            DayPanel(
                state = state,
                onAdd = { adding = true },
                onDeleteEvent = vm::deleteEvent,
                onToggleTask = vm::toggleTask,
                modifier = m,
            )
        }

        if (maxWidth >= 720.dp) {
            // Wide: month on the left, the selected day on the right
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                month(Modifier.widthIn(max = 480.dp).weight(1f))
                day(Modifier.weight(1f))
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

    if (adding) {
        AddEventDialog(
            date = state.selected,
            onDismiss = { adding = false },
            onSave = { title, start, end ->
                vm.addEvent(title, state.selected, start, end)
                adding = false
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
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                monthTitle(state.month),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToday) { Text("Today") }
            TextButton(onClick = onPrevious) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            TextButton(onClick = onNext) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        Spacer(Modifier.height(8.dp))

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

        // 42 days -> 6 rows of 7
        state.days.chunked(7).forEach { week ->
            Row {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        inMonth = date.month == state.month.month,
                        isToday = date == state.today,
                        isSelected = date == state.selected,
                        hasEvents = state.events[date].orEmpty().isNotEmpty(),
                        hasTasks = state.tasks[date].orEmpty().isNotEmpty(),
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvents: Boolean,
    hasTasks: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(shape)
            .background(if (isSelected) colors.primary else Color.Transparent)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, colors.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.day.toString(),
                fontWeight = if (isToday) FontWeight.Bold else null,
                color = when {
                    isSelected -> colors.onPrimary
                    !inMonth -> colors.onSurfaceVariant.copy(alpha = 0.35f)
                    isToday -> colors.primary
                    else -> colors.onBackground
                },
            )
            // Gold dot = event(s), grey dot = task(s) due
            Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (hasEvents) Dot(if (isSelected) colors.onPrimary else colors.primary)
                if (hasTasks) Dot(if (isSelected) colors.onPrimary else colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))
}

// ---------- Selected day ----------

@Composable
private fun DayPanel(
    state: CalendarUiState,
    onAdd: () -> Unit,
    onDeleteEvent: (String) -> Unit,
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
            Button(onClick = onAdd) { Text("Add event") }
        }
        Spacer(Modifier.height(12.dp))

        if (state.selectedEvents.isEmpty() && state.selectedTasks.isEmpty()) {
            Text("Nothing planned.", color = colors.onSurfaceVariant)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Prefixed keys: an event and a task can never be confused for each other.
            items(state.selectedEvents, key = { "e-${it.id}" }) { event ->
                EventRow(event, onDelete = { onDeleteEvent(event.id) })
            }
            items(state.selectedTasks, key = { "t-${it.id}" }) { task ->
                DueTaskRow(task, onToggle = { onToggleTask(task.id) })
            }
        }
    }
}

@Composable
private fun EventRow(event: CalendarEvent, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                timeLabel(event),
                modifier = Modifier.width(96.dp),
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
            )
            Text(event.title, modifier = Modifier.weight(1f), color = colors.onSurface)
            TextButton(onClick = onDelete) { Text("✕", color = colors.onSurfaceVariant) }
        }
    }
}

/** A task due this day. Ticking it here is the same as ticking it on the Tasks page. */
@Composable
private fun DueTaskRow(task: Task, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
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
    val start = event.start ?: return "All day"
    val end = event.end ?: return hhmm(start)
    return "${hhmm(start)}–${hhmm(end)}"
}

// ---------- Add event dialog ----------

@Composable
private fun AddEventDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, start: LocalTime?, end: LocalTime?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(true) }
    var start by remember { mutableStateOf(LocalTime(9, 0)) }
    var end by remember { mutableStateOf(LocalTime(10, 0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New event · ${shortDate(date)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allDay, onCheckedChange = { allDay = it })
                    Text("All day")
                }
                if (!allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeButton("Start", start, onPick = {
                            start = it
                            // Keep the end after the start.
                            if (end <= it) end = it.oneHourLater()
                        })
                        TimeButton("End", end, onPick = { end = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, start.takeUnless { allDay }, end.takeUnless { allDay }) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
