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
        assertEquals("Good Morning", greetingFor(8))
        assertEquals("Good Afternoon", greetingFor(14))
        assertEquals("Good Evening", greetingFor(21))
        assertEquals("Good Evening", greetingFor(2))
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
}
