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
import dev.ame.polaris.ui.DateRangeButton
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
    endDate: LocalDate? = null,
): CalendarEvent? {
    val clean = title.trim()
    if (clean.isEmpty()) return null
    // An end date before the start date can't happen; the same day is stored as "no end date".
    val lastDay = endDate?.takeIf { it > date }
    // No end time without a start. On a one-day event an end before the start is dropped;
    // on a multi-day event it's fine (it ends on a later day).
    val fixedEnd = when {
        start == null -> null
        lastDay == null && end != null && end < start -> null
        else -> end
    }
    return CalendarEvent(
        id = existing?.id ?: newId(),
        title = clean,
        date = date,
        start = start,
        end = fixedEnd,
        createdAt = existing?.createdAt ?: nowMillis(),
        endDate = lastDay,
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
    onSave: (title: String, date: LocalDate, start: LocalTime?, end: LocalTime?, endDate: LocalDate) -> Unit,
    onDelete: (() -> Unit)? = null,
    initialStart: LocalTime? = null,
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var day by remember { mutableStateOf(existing?.date ?: date) }
    var lastDay by remember { mutableStateOf(existing?.lastDate ?: date) }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allDay, onCheckedChange = { allDay = it })
                    Text("All day")
                }
                // One calendar for the dates: tap one day, or a first and a last day
                Text("DATES", style = MaterialTheme.typography.labelMedium)
                DateRangeButton(day, lastDay, onPick = { from, to ->
                    day = from
                    lastDay = to
                })
                if (!allDay) {
                    // Start time is on the first day, end time on the last day
                    Text("TIME", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TimeButton("", start, onPick = {
                            start = it
                            // Keep a same-day event's end after its start.
                            if (lastDay == day && end <= it) end = it.oneHourLater()
                        })
                        Text("→")
                        TimeButton("", end, onPick = { end = it })
                    }
                }
                if (!allDay && lastDay == day && end <= start) {
                    Text("Ends before it starts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, day, start.takeUnless { allDay }, end.takeUnless { allDay }, lastDay) },
                enabled = title.isNotBlank() && (allDay || lastDay > day || end > start),
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
