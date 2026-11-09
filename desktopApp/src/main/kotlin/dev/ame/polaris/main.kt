package dev.ame.polaris

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

/**
 * The app icon (window + tray): a gold four-pointed star on a dark circle.
 * Drawn in code from the same design as design/icon/ (see make_icons.py there).
 */
private object StarIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val d = size.minDimension
        val c = d / 2
        val tip = d * 27f / 66f // the star fills ~80% of the circle, like the design
        drawCircle(Color(0xFF1E1E1E), radius = c, center = Offset(c, c))
        // Four points joined by curves that pull in to the centre
        val star = Path().apply {
            moveTo(c, c - tip)
            quadraticTo(c, c, c + tip, c)
            quadraticTo(c, c, c, c + tip)
            quadraticTo(c, c, c - tip, c)
            quadraticTo(c, c, c, c - tip)
            close()
        }
        drawPath(
            star,
            Brush.linearGradient(
                listOf(Color(0xFFEDBD37), Color(0xFFC9881F)), // light top-left -> deeper bottom-right
                start = Offset(c - tip, c - tip),
                end = Offset(c + tip, c + tip),
            ),
        )
    }
}
