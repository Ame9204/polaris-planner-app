package dev.ame.polaris

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import dev.ame.polaris.data.createDatabase
import dev.ame.polaris.reminders.Reminder
import dev.ame.polaris.reminders.ReminderEngine
import dev.ame.polaris.reminders.ReminderPlatform
import kotlinx.coroutines.delay
import kotlin.time.Instant

fun main() {
    // Open the database once, before any window exists.
    val database = createDatabase()
    val reminders = ReminderEngine(database)

    application {
        // An icon in the system tray (bottom right on Windows). Reminders pop up from it.
        val trayState = rememberTrayState()
        Tray(icon = StarIcon, state = trayState, tooltip = "Polaris")

        val platform = remember {
            object : ReminderPlatform {
                override fun show(reminder: Reminder) =
                    trayState.sendNotification(Notification(reminder.title, reminder.text, Notification.Type.Info))

                // Desktop doesn't need alarms: it simply checks every 30 seconds while open.
                override fun wakeAt(at: Instant?) = Unit
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                reminders.tick(platform)
                delay(30_000)
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Polaris",
            icon = StarIcon,
        ) {
            App(database)
        }
    }
}

/** A tangerine four-pointed star, drawn in code so no image file is needed. */
private object StarIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        val star = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w * 0.59f, h * 0.41f)
            lineTo(w, h * 0.5f)
            lineTo(w * 0.59f, h * 0.59f)
            lineTo(w * 0.5f, h)
            lineTo(w * 0.41f, h * 0.59f)
            lineTo(0f, h * 0.5f)
            lineTo(w * 0.41f, h * 0.41f)
            close()
        }
        drawPath(star, Color(0xFFE9A25A)) // tangerine
    }
}
