package dev.ame.polaris

import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import dev.ame.polaris.data.put
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import dev.ame.polaris.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlin.time.Instant

/** Settings key: has the "still running in the tray" tip been shown yet? */
private const val TRAY_HINT = "desktop.trayHintShown"

fun main() {
    // Open the database once, before any window exists.
    val database = createDatabase()
    val reminders = ReminderEngine(database)
    // Cloud sync, running in the background while the app is open
    val sync = SyncManager(database, CoroutineScope(SupervisorJob() + Dispatchers.Default)).apply { start() }

    application {
        // Closing the window only hides it: Polaris keeps running in the tray, so reminders
        // and sync keep working. Quit for real from the tray icon's menu.
        var windowVisible by remember { mutableStateOf(true) }
        val windowState = rememberWindowState()
        val scope = rememberCoroutineScope()
        val settings = remember { database.settingDao() }

        fun showWindow() {
            windowVisible = true
            windowState.isMinimized = false
        }

        // An icon in the system tray (bottom right on Windows). Reminders pop up from it.
        // Click it to open Polaris; right-click for the menu.
        val trayState = rememberTrayState()
        Tray(
            icon = StarIcon,
            state = trayState,
            tooltip = "Polaris",
            onAction = ::showWindow,
            menu = {
                Item("Open Polaris", onClick = ::showWindow)
                Item("Sync now", onClick = sync::syncNow)
                Separator()
                Item("Quit Polaris", onClick = ::exitApplication)
            },
        )

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
            onCloseRequest = {
                windowVisible = false
                // The very first time, explain where it went
                scope.launch {
                    if (settings.get(TRAY_HINT) == null) {
                        settings.put(TRAY_HINT, "shown")
                        trayState.sendNotification(
                            Notification(
                                "Polaris is still running",
                                "Reminders and sync keep working in the tray. Right-click the star to quit.",
                                Notification.Type.Info,
                            )
                        )
                    }
                }
            },
            visible = windowVisible,
            state = windowState,
            title = "Polaris",
            icon = StarIcon,
        ) {
            // Reopened from the tray: bring the window to the front
            LaunchedEffect(windowVisible) {
                if (windowVisible) {
                    window.toFront()
                    window.requestFocus()
                }
            }
            App(database, sync)
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
