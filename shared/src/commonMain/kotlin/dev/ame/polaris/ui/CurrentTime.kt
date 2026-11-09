package dev.ame.polaris.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The current time of day, refreshed every 30 seconds while the screen is showing.
 * Anything that reads it (a "Now" highlight, the greeting) updates on its own.
 */
@Composable
fun rememberCurrentTime(): State<LocalTime> = produceState(currentTime()) {
    while (true) {
        delay(30_000)
        value = currentTime()
    }
}

fun currentTime(): LocalTime =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
