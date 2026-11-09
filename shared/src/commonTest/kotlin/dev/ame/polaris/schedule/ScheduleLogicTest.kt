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

    // ---- Timeline layout ----

    private fun lanes(vararg blocks: ScheduleBlock) =
        layoutLanes(blocks.toList()).associate { it.block.id to (it.lane to it.laneCount) }

    @Test
    fun separateBlocksGetTheFullWidth() {
        val result = lanes(
            block("Gym", LocalTime(7, 0), LocalTime(8, 0)),
            block("Work", LocalTime(8, 0), LocalTime(9, 0)), // touching isn't overlapping
        )
        assertEquals(mapOf("Gym" to (0 to 1), "Work" to (0 to 1)), result)
    }

    @Test
    fun overlappingBlocksSitSideBySide() {
        val result = lanes(
            block("Long", LocalTime(9, 0), LocalTime(12, 0)),
            block("Call", LocalTime(9, 30), LocalTime(10, 0)),
            block("Lunch", LocalTime(12, 0), LocalTime(13, 0)), // after the overlap: full width again
        )
        assertEquals((0 to 2), result["Long"])
        assertEquals((1 to 2), result["Call"])
        assertEquals((0 to 1), result["Lunch"])
    }

    @Test
    fun aFreedLaneIsReused() {
        val result = lanes(
            block("A", LocalTime(9, 0), LocalTime(12, 0)),
            block("B", LocalTime(9, 0), LocalTime(10, 0)),
            block("C", LocalTime(10, 30), LocalTime(11, 0)), // B's lane is free again
        )
        assertEquals((1 to 2), result["C"])
    }

    @Test
    fun weeksStartOnMonday() {
        val week = weekOf(kotlinx.datetime.LocalDate(2026, 11, 12)) // a Thursday
        assertEquals(kotlinx.datetime.LocalDate(2026, 11, 9), week.first())
        assertEquals(kotlinx.datetime.LocalDate(2026, 11, 15), week.last())
        assertEquals("9 – 15 Nov", weekLabel(week))
        assertEquals("30 Nov – 6 Dec", weekLabel(weekOf(kotlinx.datetime.LocalDate(2026, 12, 2))))
    }

    @Test
    fun happensOnChecksDateOrWeekday() {
        val monday = kotlinx.datetime.LocalDate(2026, 11, 9)
        val weekdays = ScheduleBlock("w", "Work", LocalTime(9, 0), LocalTime(17, 0), days = Weekdays.toBits(), createdAt = 0)
        val once = ScheduleBlock("o", "Once", LocalTime(9, 0), LocalTime(10, 0), date = monday, createdAt = 0)
        assertEquals(true, weekdays.happensOn(monday))
        assertEquals(false, weekdays.happensOn(kotlinx.datetime.LocalDate(2026, 11, 14))) // Saturday
        assertEquals(true, once.happensOn(monday))
        assertEquals(false, once.happensOn(kotlinx.datetime.LocalDate(2026, 11, 16))) // next Monday
    }

    @Test
    fun eventsAndBlocksShareLanes() {
        val work = block("Work", LocalTime(9, 0), LocalTime(17, 0))
        val dentist = dev.ame.polaris.calendar.CalendarEvent("e", "Dentist", kotlinx.datetime.LocalDate(2026, 11, 9), LocalTime(14, 0), createdAt = 1)
        val placed = layoutEntries(timelineEntries(listOf(work), listOf(dentist)))
        val byName = placed.associate {
            val name = when (val e = it.item) {
                is TimelineEntry.Block -> e.block.title
                is TimelineEntry.Event -> e.event.title
            }
            name to (it.lane to it.laneCount)
        }
        assertEquals((0 to 2), byName["Work"])
        assertEquals((1 to 2), byName["Dentist"])
    }

    @Test
    fun eventsWithoutAnEndAreHalfAnHour() {
        val day = kotlinx.datetime.LocalDate(2026, 11, 9)
        assertEquals(LocalTime(14, 30), eventEnd(dev.ame.polaris.calendar.CalendarEvent("e", "x", day, LocalTime(14, 0), createdAt = 0)))
        assertEquals(LocalTime(23, 59), eventEnd(dev.ame.polaris.calendar.CalendarEvent("e", "x", day, LocalTime(23, 50), createdAt = 0)))
    }

    @Test
    fun hourLabelsAre24Hour() {
        assertEquals(listOf("00:00", "01:00", "11:00", "12:00", "13:00", "23:00"), listOf(0, 1, 11, 12, 13, 23).map(::hourLabel))
    }

    @Test
    fun tappingSnapsToTheHalfHour() {
        val hourPx = 100f
        assertEquals(LocalTime(9, 0), timeAtOffset(9 * hourPx + 20, hourPx))  // 9:12 -> 9:00
        assertEquals(LocalTime(9, 30), timeAtOffset(9 * hourPx + 55, hourPx)) // 9:33 -> 9:30
        assertEquals(LocalTime(23, 30), timeAtOffset(24 * hourPx + 50, hourPx)) // past the end -> last slot
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
