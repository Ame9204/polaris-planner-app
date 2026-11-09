package dev.ame.polaris.reminders

import dev.ame.polaris.calendar.EventDao
import dev.ame.polaris.data.PolarisDatabase
import dev.ame.polaris.data.SettingDao
import dev.ame.polaris.data.put
import dev.ame.polaris.schedule.ScheduleDao
import dev.ame.polaris.schedule.bit
import dev.ame.polaris.tasks.TaskDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What each platform does to actually notify you. */
interface ReminderPlatform {
    /** Show this notification now. */
    fun show(reminder: Reminder)

    /**
     * Make sure [ReminderEngine.tick] runs again at [at] (null = nothing coming up).
     * Android sets an alarm; desktop ignores it because it ticks every 30 s while open.
     */
    fun wakeAt(at: Instant?)
}

/**
 * The shared brain of reminders. Call [tick] whenever something might be due:
 * on start, when data changes, and when the platform wakes it up.
 *
 * Each tick shows every reminder whose time fell between the previous tick and now,
 * then asks to be woken for the next one. Remembering the previous tick (in the settings
 * table) is what makes sure nothing is shown twice and nothing is skipped.
 */
class ReminderEngine(
    private val events: EventDao,
    private val tasks: TaskDao,
    private val schedule: ScheduleDao,
    private val settings: SettingDao,
    private val rules: ReminderRules = ReminderRules.Default,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    constructor(db: PolarisDatabase) : this(db.eventDao(), db.taskDao(), db.scheduleDao(), db.settingDao())

    private val lock = Mutex()

    suspend fun tick(platform: ReminderPlatform, now: Instant = Clock.System.now()) = lock.withLock {
        val zone = zone()
        val last = settings.get(LastTickKey)?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
        // First run ever: don't fire old reminders. After a long time off (phone dead,
        // app closed): only catch up on the last hour, not a flood of stale ones.
        val since = if (last == null) now else maxOf(last, now - 1.hours)

        val from = since.toLocalDateTime(zone).date
        val to = now.toLocalDateTime(zone).date.plus(1, DateTimeUnit.DAY) // through tomorrow
        val days = daysBetween(from, to)

        val planned = planReminders(
            from = from,
            to = to,
            events = events.observeBetween(from, to).first(),
            tasks = tasks.observeAll().first(), // planReminders picks the ones in range
            blocksByDay = days.associateWith { schedule.observeForDay(it, it.dayOfWeek.bit()).first() },
            rules = rules,
        ).map { it to it.at.toInstant(zone) }

        planned.filter { (_, at) -> at > since && at <= now }.forEach { (reminder, _) -> platform.show(reminder) }
        settings.put(LastTickKey, now.toEpochMilliseconds().toString())
        platform.wakeAt(planned.map { it.second }.filter { it > now }.minOrNull())
    }

    companion object {
        const val LastTickKey = "reminders.lastTick"

        /** Tables whose changes can add, move or remove a reminder. */
        val WatchedTables = arrayOf("events", "tasks", "schedule_blocks")
    }
}
