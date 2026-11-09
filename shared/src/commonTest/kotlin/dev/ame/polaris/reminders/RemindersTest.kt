package dev.ame.polaris.reminders

import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.calendar.FakeEventDao
import dev.ame.polaris.schedule.FakeScheduleDao
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.schedule.Weekdays
import dev.ame.polaris.schedule.toBits
import dev.ame.polaris.tasks.FakeTaskDao
import dev.ame.polaris.tasks.Task
import dev.ame.polaris.weather.FakeSettingDao
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class PlanRemindersTest {

    private val mon = LocalDate(2026, 11, 9)
    private val tue = LocalDate(2026, 11, 10)

    @Test
    fun eachKindGetsItsReminderTime() {
        val plan = planReminders(
            from = mon, to = mon,
            events = listOf(
                CalendarEvent("e1", "Dentist", mon, LocalTime(14, 0), createdAt = 1),
                CalendarEvent("e2", "Holiday", mon, createdAt = 2),
            ),
            tasks = listOf(Task("t1", "Pay rent", due = mon, createdAt = 1)),
            blocksByDay = mapOf(mon to listOf(ScheduleBlock("b1", "Gym", LocalTime(7, 0), LocalTime(8, 0), days = 127, createdAt = 1))),
        )
        assertEquals(
            listOf(
                "Gym" to LocalDateTime(2026, 11, 9, 7, 0),
                "Holiday" to LocalDateTime(2026, 11, 9, 8, 0),
                "Task due today" to LocalDateTime(2026, 11, 9, 9, 0),
                "Dentist" to LocalDateTime(2026, 11, 9, 13, 50),
            ),
            plan.map { it.title to it.at },
        )
        assertEquals("Starts at 14:00 (in 10 min)", plan.last().text)
    }

    @Test
    fun reminderBeforeAJustAfterMidnightEventIsTheDayBefore() {
        val plan = planReminders(
            from = mon, to = tue,
            events = listOf(CalendarEvent("e", "Late show", tue, LocalTime(0, 5), createdAt = 1)),
            tasks = emptyList(), blocksByDay = emptyMap(),
        )
        assertEquals(LocalDateTime(2026, 11, 9, 23, 55), plan.single().at)
    }

    @Test
    fun doneTasksAndDisabledKindsAreSkipped() {
        val plan = planReminders(
            from = mon, to = mon,
            events = listOf(CalendarEvent("e", "Holiday", mon, createdAt = 1)),
            tasks = listOf(Task("t", "Done", due = mon, done = true, createdAt = 1)),
            blocksByDay = mapOf(mon to listOf(ScheduleBlock("b", "Gym", LocalTime(7, 0), LocalTime(8, 0), days = 127, createdAt = 1))),
            rules = ReminderRules(allDayEventsAt = null, scheduleBlocks = false),
        )
        assertTrue(plan.isEmpty())
    }

    @Test
    fun aTasksOwnReminderReplacesTheDueDateOne() {
        val plan = planReminders(
            from = mon, to = tue,
            events = emptyList(),
            tasks = listOf(
                Task("t1", "Call bank", due = mon, createdAt = 1, remindAt = LocalDateTime(2026, 11, 9, 15, 30)),
                Task("t2", "No due date", createdAt = 2, remindAt = LocalDateTime(2026, 11, 10, 8, 0)),
                Task("t3", "Done", createdAt = 3, done = true, remindAt = LocalDateTime(2026, 11, 9, 10, 0)),
            ),
            blocksByDay = emptyMap(),
        )
        assertEquals(
            listOf("Call bank" to LocalDateTime(2026, 11, 9, 15, 30), "No due date" to LocalDateTime(2026, 11, 10, 8, 0)),
            plan.map { it.title to it.at },
        )
    }

    @Test
    fun eachBlockUsesItsOwnReminderSetting() {
        val blocks = listOf(
            ScheduleBlock("a", "At start", LocalTime(7, 0), LocalTime(8, 0), days = 127, createdAt = 1, remindMinutesBefore = 0),
            ScheduleBlock("b", "Ten before", LocalTime(9, 0), LocalTime(10, 0), days = 127, createdAt = 2, remindMinutesBefore = 10),
            ScheduleBlock("c", "Off", LocalTime(11, 0), LocalTime(12, 0), days = 127, createdAt = 3, remindMinutesBefore = null),
        )
        val plan = planReminders(mon, mon, emptyList(), emptyList(), mapOf(mon to blocks))
        assertEquals(
            listOf("At start" to LocalDateTime(2026, 11, 9, 7, 0), "Ten before" to LocalDateTime(2026, 11, 9, 8, 50)),
            plan.map { it.title to it.at },
        )
        assertEquals("Starts at 09:00 (in 10 min)", plan[1].text)
    }

    @Test
    fun repeatingBlocksHaveADifferentKeyEachDay() {
        val gym = ScheduleBlock("b", "Gym", LocalTime(7, 0), LocalTime(8, 0), days = 127, createdAt = 1)
        val plan = planReminders(mon, tue, emptyList(), emptyList(), mapOf(mon to listOf(gym), tue to listOf(gym)))
        assertEquals(2, plan.map { it.key }.toSet().size)
    }
}

class ReminderEngineTest {

    private val events = FakeEventDao()
    private val tasks = FakeTaskDao()
    private val schedule = FakeScheduleDao()
    private val settings = FakeSettingDao()
    private val engine = ReminderEngine(events, tasks, schedule, settings, zone = { TimeZone.UTC })

    /** Records what the engine asks the platform to do. */
    private class RecordingPlatform : ReminderPlatform {
        val shown = mutableListOf<String>()
        var wake: Instant? = null
        override fun show(reminder: Reminder) { shown += reminder.title }
        override fun wakeAt(at: Instant?) { wake = at }
    }

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime(2026, 11, day, hour, minute).toInstant(TimeZone.UTC)

    @Test
    fun showsWhatBecameDueAndSetsTheNextWakeUp() = runTest {
        events.upsert(CalendarEvent("e", "Dentist", LocalDate(2026, 11, 9), LocalTime(14, 0), createdAt = 1))
        schedule.upsert(ScheduleBlock("w", "Work", LocalTime(9, 0), LocalTime(17, 0), days = Weekdays.toBits(), createdAt = 1))
        val platform = RecordingPlatform()

        engine.tick(platform, now = at(9, 8, 0)) // first run: nothing old is shown
        assertTrue(platform.shown.isEmpty())
        assertEquals(at(9, 9, 0), platform.wake) // next: Work at 09:00

        engine.tick(platform, now = at(9, 9, 0))
        assertEquals(listOf("Work"), platform.shown)
        assertEquals(at(9, 13, 50), platform.wake) // next: 10 min before the dentist
    }

    @Test
    fun aReminderIsNeverShownTwice() = runTest {
        schedule.upsert(ScheduleBlock("w", "Work", LocalTime(9, 0), LocalTime(17, 0), days = Weekdays.toBits(), createdAt = 1))
        val platform = RecordingPlatform()

        engine.tick(platform, now = at(9, 8, 59))
        engine.tick(platform, now = at(9, 9, 0))
        engine.tick(platform, now = at(9, 9, 0, ))
        engine.tick(platform, now = at(9, 9, 1))

        assertEquals(listOf("Work"), platform.shown)
    }

    @Test
    fun afterALongGapOnlyTheLastHourIsCaughtUp() = runTest {
        tasks.upsert(Task("t", "Old task", due = LocalDate(2026, 11, 9), createdAt = 1)) // 09:00 Monday
        val platform = RecordingPlatform()

        engine.tick(platform, now = at(9, 6, 0))
        engine.tick(platform, now = at(9, 18, 0)) // phone was off all day

        assertTrue(platform.shown.isEmpty()) // 09:00 is long gone: no stale ping at 18:00
    }

    @Test
    fun nothingComingUpMeansNoWakeUp() = runTest {
        val platform = RecordingPlatform()
        engine.tick(platform, now = at(9, 12, 0))
        assertNull(platform.wake)
    }
}
