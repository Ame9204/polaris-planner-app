package dev.ame.polaris

import android.app.Application
import dev.ame.polaris.data.PolarisDatabase
import dev.ame.polaris.data.createDatabase
import dev.ame.polaris.reminders.ReminderEngine
import dev.ame.polaris.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lives as long as the app process. The database is opened here, not in MainActivity,
 * because Android recreates the Activity on every screen rotation.
 */
class PolarisApp : Application() {
    val database: PolarisDatabase by lazy { createDatabase(this) }

    /** For background work that isn't tied to a screen (reminders). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val reminders by lazy { ReminderEngine(database) }

    /** Cloud sync, running for as long as the app process lives. */
    val sync: SyncManager by lazy { SyncManager(database, appScope) }
    private val reminderPlatform by lazy { AndroidReminderPlatform(this) }

    override fun onCreate() {
        super.onCreate()
        reminderPlatform.createChannel()
        sync.start()

        // Re-plan reminders now, and again every time events, tasks or schedule blocks change,
        // so a new event's alarm is set the moment you save it.
        appScope.launch {
            database.invalidationTracker
                .createFlow(*ReminderEngine.WatchedTables)
                .collect { tickReminders() }
        }
    }

    suspend fun tickReminders() = reminders.tick(reminderPlatform)
}
