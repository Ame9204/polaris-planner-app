package dev.ame.polaris.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ame.polaris.ui.EmptyState
import dev.ame.polaris.ui.ItemGap
import dev.ame.polaris.ui.LocalUndo
import dev.ame.polaris.ui.PageHeader
import dev.ame.polaris.ui.PagePadding
import dev.ame.polaris.ui.PolarisIcons
import dev.ame.polaris.ui.QuietIconButton
import dev.ame.polaris.ui.ReadableWidth
import dev.ame.polaris.ui.DateButton
import dev.ame.polaris.ui.TimeButton
import dev.ame.polaris.ui.currentTime
import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.shortDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import dev.ame.polaris.ui.GoldIconButton
import dev.ame.polaris.ui.PolarisCard

@Composable
fun TasksScreen(
    dao: TaskDao,
    modifier: Modifier = Modifier,
    // viewModel { } creates it once and keeps it while the app runs.
    vm: TasksViewModel = viewModel { TasksViewModel(dao) },
) {
    // collectAsState turns the StateFlow into Compose state: when the list changes, this redraws.
    val tasks by vm.tasks.collectAsState()
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val undo = LocalUndo.current

    // Undone first, sorted by due date (no date last); done tasks after.
    val todo = tasks.filter { !it.done }.sortedWith(compareBy(nullsLast()) { it.due })
    val done = tasks.filter { it.done }

    // The task whose "Remind me" dialog is open, if any
    var remindingTask by remember { mutableStateOf<Task?>(null) }

    fun delete(task: Task) {
        vm.delete(task.id)
        undo.offer("Task deleted") { vm.restore(task) }
    }

    Column(modifier.fillMaxSize().padding(PagePadding).widthIn(max = ReadableWidth)) {
        PageHeader(
            "Tasks",
            subtitle = when {
                tasks.isEmpty() -> null
                todo.isEmpty() -> "All done"
                else -> "${todo.size} to do"
            },
        )
        Spacer(Modifier.height(16.dp))

        AddTaskRow(onAdd = vm::add)
        Spacer(Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            EmptyState(
                PolarisIcons.Tasks,
                "No tasks yet",
                "Add one above. Give it a due date to see it on the Calendar.",
            )
        }

        LazyColumn(verticalArrangement = ItemGap) {
            // animateItem(): rows slide into place when the list changes, instead of jumping.
            items(todo, key = { it.id }) { task ->
                TaskRow(
                    task, today,
                    onToggle = { vm.toggleDone(task.id) },
                    onReminder = { remindingTask = task },
                    onDelete = { delete(task) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (done.isNotEmpty()) {
                item(key = "done-header") {
                    Text(
                        "Done",
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).animateItem(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(done, key = { it.id }) { task ->
                    TaskRow(
                        task, today,
                        onToggle = { vm.toggleDone(task.id) },
                        onReminder = { remindingTask = task },
                        onDelete = { delete(task) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    remindingTask?.let { task ->
        ReminderDialog(
            task = task,
            today = today,
            onDismiss = { remindingTask = null },
            onSave = { at ->
                vm.setReminder(task.id, at)
                remindingTask = null
            },
        )
    }
}

/**
 * "Remind me" for one task: a date and a time. Opens on the task's current reminder,
 * or a sensible guess (its due date, or today, at the next hour / 09:00).
 */
@Composable
private fun ReminderDialog(task: Task, today: LocalDate, onDismiss: () -> Unit, onSave: (LocalDateTime?) -> Unit) {
    val guess = remember(task) {
        task.remindAt ?: run {
            val day = task.due?.takeIf { it >= today } ?: today
            val time = if (day == today) LocalTime(minOf(currentTime().hour + 1, 23), 0) else LocalTime(9, 0)
            day.atTime(time)
        }
    }
    var date by remember { mutableStateOf(guess.date) }
    var time by remember { mutableStateOf(guess.time) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(PolarisIcons.Bell, contentDescription = null) },
        title = { Text("Remind me") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateButton("Date", date, onPick = { date = it })
                    TimeButton("Time", time, onPick = { time = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(date.atTime(time)) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (task.remindAt != null) {
                    TextButton(onClick = { onSave(null) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Text field + optional due date + Add button. Keeps its own typing state until you press Add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTaskRow(onAdd: (String, LocalDate?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var due by remember { mutableStateOf<LocalDate?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    fun submit() {
        onAdd(title, due)
        title = ""
        due = null
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("New task") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { pickingDate = true }) {
            Icon(PolarisIcons.Calendar, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
            Text(due?.let(::shortDate) ?: "Due")
        }
        GoldIconButton(onClick = ::submit, enabled = title.isNotBlank()) {
            Icon(PolarisIcons.Add, contentDescription = "Add task")
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    // The picker gives milliseconds at midnight UTC; convert to a plain date.
                    due = state.selectedDateMillis?.let {
                        Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { due = null; pickingDate = false }) { Text("No date") }
            },
        ) {
            DatePicker(state)
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    today: LocalDate,
    onToggle: () -> Unit,
    onReminder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    PolarisCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = colors.primary,
                    checkmarkColor = colors.onPrimary,
                    uncheckedColor = colors.onSurfaceVariant,
                ),
            )
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.done) colors.onSurfaceVariant else colors.onSurface,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                task.due?.let { due ->
                    val overdue = !task.done && due < today
                    Text(
                        dueLabel(due, today),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) colors.error else colors.primary,
                    )
                }
                task.remindAt?.let { at ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(PolarisIcons.Bell, contentDescription = "Reminder", tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        Text(
                            " ${shortDate(at.date)}, ${hhmm(at.time)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            // The bell is gold when this task has its own reminder
            IconButton(onClick = onReminder) {
                Icon(
                    PolarisIcons.Bell,
                    contentDescription = if (task.remindAt != null) "Change reminder" else "Add reminder",
                    tint = if (task.remindAt != null) colors.primary else colors.onSurfaceVariant,
                )
            }
            QuietIconButton(PolarisIcons.Delete, "Delete task", onClick = onDelete)
        }
    }
}

private fun dueLabel(due: LocalDate, today: LocalDate): String = when {
    due == today -> "Today"
    due < today -> "Overdue · ${shortDate(due)}"
    due.toEpochDays() - today.toEpochDays() == 1L -> "Tomorrow"
    else -> shortDate(due)
}
