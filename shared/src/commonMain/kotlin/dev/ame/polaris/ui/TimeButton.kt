package dev.ame.polaris.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.ame.polaris.util.hhmm
import kotlinx.datetime.LocalTime

/**
 * A button showing e.g. "Start 09:00". Tapping it opens a small dialog to type a time.
 * Reusable: used by both the Calendar and Schedule pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeButton(
    label: String,
    time: LocalTime,
    onPick: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text(if (label.isBlank()) hhmm(time) else "$label ${hhmm(time)}")
    }

    if (open) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            text = { TimeInput(state) },
            confirmButton = {
                TextButton(onClick = {
                    onPick(LocalTime(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
