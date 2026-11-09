package dev.ame.polaris.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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

    // Undone first, sorted by due date (no date last); done tasks after.
    val todo = tasks.filter { !it.done }.sortedWith(compareBy(nullsLast()) { it.due })
    val done = tasks.filter { it.done }

    Column(modifier.fillMaxSize().padding(24.dp)) {
        Text(
            "Tasks",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))

        AddTaskRow(onAdd = vm::add)
        Spacer(Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Text("Nothing to do. Add a task above.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(todo, key = { it.id }) { task ->
                TaskRow(task, today, onToggle = { vm.toggleDone(task.id) }, onDelete = { vm.delete(task.id) })
            }
            if (done.isNotEmpty()) {
                item {
                    Text(
                        "Done",
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(done, key = { it.id }) { task ->
                    TaskRow(task, today, onToggle = { vm.toggleDone(task.id) }, onDelete = { vm.delete(task.id) })
                }
            }
        }
    }
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
            Text(due?.let(::shortDate) ?: "Due")
        }
        Button(onClick = ::submit, enabled = title.isNotBlank()) {
            Text("Add")
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
private fun TaskRow(task: Task, today: LocalDate, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                task.due?.let { due ->
                    val overdue = !task.done && due < today
                    Text(
                        dueLabel(due, today),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            TextButton(onClick = onDelete) {
                Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun dueLabel(due: LocalDate, today: LocalDate): String = when {
    due == today -> "Today"
    due < today -> "Overdue · ${shortDate(due)}"
    due.toEpochDays() - today.toEpochDays() == 1L -> "Tomorrow"
    else -> shortDate(due)
}

/** e.g. "12 Nov" */
private fun shortDate(date: LocalDate): String =
    "${date.day} ${date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
