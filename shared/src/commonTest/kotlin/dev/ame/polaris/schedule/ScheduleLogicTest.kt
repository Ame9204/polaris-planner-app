package dev.ame.polaris.schedule

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleLogicTest {

    private fun block(id: String, start: LocalTime, end: LocalTime) =
        ScheduleBlock(id = id, title = id, start = start, end = end, days = 127, createdAt = 0)

    @Test
    fun daysRoundTripThroughBits() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertEquals(1 + 4 + 64, days.toBits())
        assertEquals(days, daysFromBits(days.toBits()))
    }

    @Test
    fun repeatLabels() {
        assertEquals("Once", repeatLabel(0))
        assertEquals("Every day", repeatLabel(AllDays.toBits()))
        assertEquals("Weekdays", repeatLabel(Weekdays.toBits()))
        assertEquals("Weekends", repeatLabel(Weekend.toBits()))
        assertEquals("Mon, Wed, Fri", repeatLabel(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY).toBits()))
    }

    @Test
    fun durations() {
        assertEquals("45 min", durationLabel(45))
        assertEquals("2 h", durationLabel(120))
        assertEquals("1 h 30 min", durationLabel(90))
    }

    @Test
    fun timelineAddsFreeTimeBetweenBlocks() {
        val items = timeline(
            listOf(
                block("Lunch", LocalTime(12, 0), LocalTime(13, 0)),
                block("Gym", LocalTime(7, 0), LocalTime(8, 0)),
                block("Coffee", LocalTime(8, 5), LocalTime(8, 30)), // 5 min gap: too short to show
            )
        )
        assertEquals(
            listOf(
                TimelineItem.Block(block("Gym", LocalTime(7, 0), LocalTime(8, 0))),
                TimelineItem.Block(block("Coffee", LocalTime(8, 5), LocalTime(8, 30))),
                TimelineItem.Free(LocalTime(8, 30), LocalTime(12, 0)),
                TimelineItem.Block(block("Lunch", LocalTime(12, 0), LocalTime(13, 0))),
            ),
            items,
        )
    }

    @Test
    fun overlappingBlocksDontCreateFakeFreeTime() {
        val items = timeline(
            listOf(
                block("Long", LocalTime(9, 0), LocalTime(12, 0)),
                block("Inside", LocalTime(9, 30), LocalTime(10, 0)),
                block("After", LocalTime(12, 0), LocalTime(13, 0)),
            )
        )
        assertEquals(0, items.count { it is TimelineItem.Free })
    }

    @Test
    fun happeningNowIncludesStartButNotEnd() {
        val b = block("B", LocalTime(9, 0), LocalTime(10, 0))
        assertEquals(true, b.isHappeningAt(LocalTime(9, 0)))
        assertEquals(false, b.isHappeningAt(LocalTime(10, 0)))
    }

    @Test
    fun oneHourLaterStopsAtMidnight() {
        assertEquals(LocalTime(10, 15), LocalTime(9, 15).oneHourLater())
        assertEquals(LocalTime(23, 59), LocalTime(23, 30).oneHourLater())
    }
}
