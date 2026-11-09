package dev.ame.polaris.ui

import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.ame.polaris.util.shortDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * A button showing a day ("9 Nov") or a range ("9 – 11 Nov"). Tapping it opens a calendar
 * where you tap one day for a single day, or tap a start and an end for several days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeButton(
    start: LocalDate,
    end: LocalDate,
    onPick: (start: LocalDate, end: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text(rangeLabel(start, end))
    }

    if (open) {
        // The picker works in milliseconds at midnight UTC, so convert both ways in UTC.
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = start.toPickerMillis(),
            initialSelectedEndDateMillis = end.takeIf { it > start }?.toPickerMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val from = state.selectedStartDateMillis?.toPickerDate()
                        if (from != null) {
                            // Only one day tapped: a one-day event
                            val to = state.selectedEndDateMillis?.toPickerDate() ?: from
                            onPick(from, to)
                        }
                        open = false
                    },
                    enabled = state.selectedStartDateMillis != null,
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state, modifier = Modifier.weight(1f), showModeToggle = false)
        }
    }
}

/** "9 Nov", "9 – 11 Nov", or "30 Nov – 2 Dec". */
fun rangeLabel(start: LocalDate, end: LocalDate): String = when {
    end <= start -> shortDate(start)
    start.month == end.month && start.year == end.year -> "${start.day} – ${shortDate(end)}"
    else -> "${shortDate(start)} – ${shortDate(end)}"
}

private fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toPickerDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
