package dev.ame.polaris.overview

import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.schedule.ScheduleBlock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverviewLogicTest {

    private val today = LocalDate(2026, 11, 9)

    private fun block(title: String, start: Int, end: Int) =
        ScheduleBlock(title, title, LocalTime(start, 0), LocalTime(end, 0), days = 127, createdAt = 0)

    @Test
    fun greetings() {
        // Every hour has a greeting, with the name in it
        for (hour in 0..23) assert("Ame" in greetingFor(hour, today)) { "hour $hour" }
        // Late night gets late-night lines, morning gets morning ones
        assert(greetingFor(1, today) != greetingFor(9, today))
        // Same all day, so it doesn't flicker
        assertEquals(greetingFor(9, today), greetingFor(10, today))
    }

    @Test
    fun nowAndNextFindsCurrentAndFollowingBlock() {
        val blocks = listOf(block("Gym", 7, 8), block("Work", 9, 17), block("Dinner", 18, 19))
        val result = nowAndNext(blocks, LocalTime(9, 30))
        assertEquals("Work", result.now?.title)
        assertEquals("Dinner", result.next?.title)
    }

    @Test
    fun betweenBlocksThereIsOnlyANext() {
        val result = nowAndNext(listOf(block("Gym", 7, 8), block("Work", 9, 17)), LocalTime(8, 30))
        assertNull(result.now)
        assertEquals("Work", result.next?.title)
    }

    @Test
    fun finishedEventsTodayAreHidden() {
        val events = listOf(
            CalendarEvent("1", "Breakfast meeting", today, LocalTime(8, 0), LocalTime(9, 0), createdAt = 1),
            CalendarEvent("2", "Holiday", today, createdAt = 2),
            CalendarEvent("3", "Call", today, LocalTime(15, 0), createdAt = 3),
            CalendarEvent("4", "Tomorrow early", LocalDate(2026, 11, 10), LocalTime(7, 0), createdAt = 4),
        )
        val shown = upcomingEvents(events, today, LocalTime(12, 0))
        assertEquals(listOf("Holiday", "Call", "Tomorrow early"), shown.map { it.title })
    }

    @Test
    fun dayLabels() {
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Tomorrow", dayLabel(LocalDate(2026, 11, 10), today))
        assertEquals("Thu 12 Nov", dayLabel(LocalDate(2026, 11, 12), today))
    }

    // ---- Countdowns ----

    @Test
    fun countdownWording() {
        assertEquals("now", countdown(0))
        assertEquals("in 25 min", countdown(25))
        assertEquals("in 2 h", countdown(120))
        assertEquals("in 2 h 10 min", countdown(130))
        assertEquals("tomorrow", countdown(24 * 60 + 5))
        assertEquals("in 3 days", countdown(3 * 24 * 60))
    }

    @Test
    fun timeLeftInTheCurrentBlock() {
        assertEquals("1 h 20 min left", timeLeft(LocalTime(15, 40), LocalTime(17, 0)))
        assertEquals("5 min left", timeLeft(LocalTime(16, 55), LocalTime(17, 0)))
    }

    @Test
    fun eventCountdowns() {
        val now = LocalTime(12, 0)
        fun ev(date: LocalDate, start: LocalTime?) = CalendarEvent("e", "x", date, start, createdAt = 0)
        assertEquals("in 2 h 30 min", eventCountdown(ev(today, LocalTime(14, 30)), today, now))
        assertEquals("happening now", eventCountdown(ev(today, LocalTime(11, 0)), today, now))
        assertEquals("today", eventCountdown(ev(today, null), today, now))
        assertEquals("in 21 h", eventCountdown(ev(LocalDate(2026, 11, 10), LocalTime(9, 0)), today, now))
        assertEquals("in 3 days", eventCountdown(ev(LocalDate(2026, 11, 12), LocalTime(9, 0)), today, now))
        assertEquals("tomorrow", eventCountdown(ev(LocalDate(2026, 11, 10), null), today, now))
    }

    @Test
    fun dueCountdowns() {
        assertEquals("due today", dueCountdown(today, today))
        assertEquals("due tomorrow", dueCountdown(LocalDate(2026, 11, 10), today))
        assertEquals("due in 4 days", dueCountdown(LocalDate(2026, 11, 13), today))
        assertEquals("1 day overdue", dueCountdown(LocalDate(2026, 11, 8), today))
        assertEquals("3 days overdue", dueCountdown(LocalDate(2026, 11, 6), today))
    }

    // ---- Schedule bar ----

    @Test
    fun barSpansAtLeastSixToTenAndStretches() {
        assertEquals(6 to 22, barHours(listOf(block("Work", 9, 17))))
        assertEquals(5 to 24, barHours(listOf(block("Early", 5, 6), ScheduleBlock("l", "Late", LocalTime(22, 0), LocalTime(23, 30), days = 127, createdAt = 0))))
    }

    @Test
    fun barFractions() {
        val hours = 6 to 22 // 16 hours wide
        assertEquals(0f, barFraction(LocalTime(6, 0), hours))
        assertEquals(0.5f, barFraction(LocalTime(14, 0), hours))
        assertEquals(1f, barFraction(LocalTime(23, 0), hours)) // past the end: clamped
    }

    // ---- Idle lines and big numbers ----

    @Test
    fun idleLineStaysTheSameAllHourAndChangesNextHour() {
        assertEquals(idleLine(today, 14), idleLine(today, 14))
        assert(idleLine(today, 14) != idleLine(today, 15))
        assert(idleLine(today, 9) in IdleLines)
    }

    @Test
    fun todaysCounts() {
        val tasks = listOf(
            dev.ame.polaris.tasks.Task("1", "Due today", due = today, createdAt = 1),
            dev.ame.polaris.tasks.Task("2", "Overdue", due = LocalDate(2026, 11, 1), createdAt = 2),
            dev.ame.polaris.tasks.Task("3", "Done", due = today, done = true, createdAt = 3),
            dev.ame.polaris.tasks.Task("4", "Later", due = LocalDate(2026, 11, 20), createdAt = 4),
        )
        val events = listOf(
            CalendarEvent("a", "Today", today, createdAt = 1),
            CalendarEvent("b", "Trip", LocalDate(2026, 11, 8), createdAt = 2, endDate = LocalDate(2026, 11, 10)),
            CalendarEvent("c", "Tomorrow", LocalDate(2026, 11, 10), createdAt = 3),
        )
        val blocks = listOf(block("Gym", 7, 8), block("Work", 9, 17))

        assertEquals(TodayStats(tasksDue = 2, eventsToday = 2, blocksLeft = 1), todayStats(tasks, events, blocks, today, LocalTime(12, 0)))
    }

    @Test
    fun hexCodes() {
        assertEquals("#E9A25A", dev.ame.polaris.ui.hexCode(dev.ame.polaris.Palette.Tangerine))
        assertEquals("#C9553D", dev.ame.polaris.ui.hexCode(dev.ame.polaris.Palette.Terracotta))
    }
}
