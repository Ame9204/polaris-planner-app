package dev.ame.polaris.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ame.polaris.ui.TimeButton
import dev.ame.polaris.ui.rememberCurrentTime
import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.longDate
import dev.ame.polaris.util.shortDate
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Composable
fun ScheduleScreen(
    dao: ScheduleDao,
    modifier: Modifier = Modifier,
    vm: ScheduleViewModel = viewModel { ScheduleViewModel(dao) },
) {
    val state by vm.state.collectAsState()
    val now by rememberCurrentTime()

    // null = closed; NewBlock = adding; a block = editing that block
    var dialog by remember { mutableStateOf<DialogTarget?>(null) }
    var confirmDelete by remember { mutableStateOf<ScheduleBlock?>(null) }

    Column(modifier.fillMaxSize().padding(24.dp)) {
        // Header: date and day navigation
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Schedule",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    if (state.isToday) "Today · ${longDate(state.date)}" else longDate(state.date),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = vm::goToToday) { Text("Today") }
            TextButton(onClick = vm::previousDay) { Text("‹", style = MaterialTheme.typography.titleLarge) }
            TextButton(onClick = vm::nextDay) { Text("›", style = MaterialTheme.typography.titleLarge) }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { dialog = DialogTarget.NewBlock }) { Text("Add block") }
        Spacer(Modifier.height(16.dp))

        if (state.blocks.isEmpty()) {
            Text("No blocks on this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.items, key = { it.key() }) { item ->
                when (item) {
                    is TimelineItem.Block -> BlockCard(
                        block = item.block,
                        isNow = state.isToday && item.block.isHappeningAt(now),
                        onClick = { dialog = DialogTarget.Edit(item.block) },
                        onDelete = {
                            // Repeating blocks ask first, since deleting removes every day's copy.
                            if (item.block.repeats) confirmDelete = item.block else vm.delete(item.block.id)
                        },
                    )
                    is TimelineItem.Free -> FreeRow(item)
                }
            }
        }
    }

    dialog?.let { target ->
        val existing = (target as? DialogTarget.Edit)?.block
        BlockDialog(
            existing = existing,
            viewedDate = state.date,
            onDismiss = { dialog = null },
            onSave = { title, start, end, days ->
                vm.save(existing, title, start, end, days)
                dialog = null
            },
        )
    }

    confirmDelete?.let { block ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${block.title}\"?") },
            text = { Text("It repeats ${repeatLabel(block.days).lowercase()}. This removes it from every day.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(block.id); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

private sealed interface DialogTarget {
    data object NewBlock : DialogTarget
    data class Edit(val block: ScheduleBlock) : DialogTarget
}

private fun TimelineItem.key(): String = when (this) {
    is TimelineItem.Block -> "b-${block.id}"
    is TimelineItem.Free -> "f-${hhmm(start)}"
}

// ---------- Rows ----------

@Composable
private fun BlockCard(block: ScheduleBlock, isNow: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = if (isNow) BorderStroke(2.dp, colors.primary) else null,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(64.dp)) {
                Text(hhmm(block.start), color = colors.primary, fontWeight = FontWeight.SemiBold)
                Text(hhmm(block.end), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f)) {
                Text(block.title, color = colors.onSurface)
                Text(
                    buildString {
                        append(durationLabel(minutesBetween(block.start, block.end)))
                        append(" · ")
                        append(repeatLabel(block.days))
                        if (isNow) append(" · Now")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isNow) colors.primary else colors.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) { Text("✕", color = colors.onSurfaceVariant) }
        }
    }
}

@Composable
private fun FreeRow(free: TimelineItem.Free) {
    Text(
        "Free · ${durationLabel(free.minutes)}",
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------- Add / edit dialog ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockDialog(
    existing: ScheduleBlock?,
    viewedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, start: LocalTime, end: LocalTime, days: Set<DayOfWeek>) -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var start by remember { mutableStateOf(existing?.start ?: LocalTime(9, 0)) }
    var end by remember { mutableStateOf(existing?.end ?: LocalTime(10, 0)) }
    var days by remember { mutableStateOf(existing?.let { daysFromBits(it.days) } ?: emptySet()) }
    val valid = title.isNotBlank() && end > start

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New block" else "Edit block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("e.g. Gym, Deep work, Class") },
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeButton("Start", start, onPick = {
                        start = it
                        if (end <= it) end = it.oneHourLater()
                    })
                    TimeButton("End", end, onPick = { end = it })
                }

                Text("Repeat", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = days.isEmpty(), onClick = { days = emptySet() }, label = { Text("Once") })
                    FilterChip(selected = days == AllDays, onClick = { days = AllDays }, label = { Text("Every day") })
                    FilterChip(selected = days == Weekdays, onClick = { days = Weekdays }, label = { Text("Weekdays") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.shortName().take(2)) },
                        )
                    }
                }

                val hint = when {
                    end <= start -> "End must be after start."
                    days.isEmpty() -> "Only on ${shortDate(existing?.date ?: viewedDate)}."
                    else -> "Repeats ${repeatLabel(days.toBits()).lowercase()}."
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (end <= start) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, start, end, days) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
