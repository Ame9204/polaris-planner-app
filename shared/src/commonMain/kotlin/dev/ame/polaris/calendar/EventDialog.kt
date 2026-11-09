package dev.ame.polaris.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.ame.polaris.schedule.oneHourLater
import dev.ame.polaris.ui.DateButton
import dev.ame.polaris.ui.TimeButton
import dev.ame.polaris.util.newId
import dev.ame.polaris.util.nowMillis
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Builds the event to save from what was typed in the dialog, or null if it's not valid
 * (no title). Keeps the id and creation time when editing [existing].
 * Shared by the Calendar and Schedule pages, so both save events the same way.
 */
fun buildEvent(
    existing: CalendarEvent?,
    title: String,
    date: LocalDate,
    start: LocalTime?,
    end: LocalTime?,
): CalendarEvent? {
    val clean = title.trim()
    if (clean.isEmpty()) return null
    // No end without a start, and an end before the start is dropped.
    val fixedEnd = if (start == null || (end != null && end < start)) null else end
    return CalendarEvent(
        id = existing?.id ?: newId(),
        title = clean,
        date = date,
        start = start,
        end = fixedEnd,
        createdAt = existing?.createdAt ?: nowMillis(),
    )
}

/**
 * Add or edit an event: title, day, all-day or start/end time.
 * Pass [existing] to edit (shows Delete when [onDelete] is given), or null to add on [date].
 * [initialStart] pre-fills a time (e.g. where you tapped on the timeline) and unticks "All day".
 */
@Composable
fun EventDialog(
    existing: CalendarEvent?,
    date: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, date: LocalDate, start: LocalTime?, end: LocalTime?) -> Unit,
    onDelete: (() -> Unit)? = null,
    initialStart: LocalTime? = null,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var day by remember { mutableStateOf(existing?.date ?: date) }
    var allDay by remember { mutableStateOf(if (existing != null) existing.isAllDay else initialStart == null) }
    var start by remember { mutableStateOf(existing?.start ?: initialStart ?: LocalTime(9, 0)) }
    var end by remember { mutableStateOf(existing?.end ?: start.oneHourLater()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New event" else "Edit event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title") },
                    singleLine = true,
                )
                DateButton("Date", day, onPick = { day = it })
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
                onClick = { onSave(title, day, start.takeUnless { allDay }, end.takeUnless { allDay }) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
