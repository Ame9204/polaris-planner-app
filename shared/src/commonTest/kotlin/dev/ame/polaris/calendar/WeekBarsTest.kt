package dev.ame.polaris.calendar

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Multi-day events drawn as continuous bars across a week row. */
class WeekBarsTest {

    // Sun 8 – Sat 14 Nov 2026
    private val week = monthGrid(LocalDate(2026, 11, 1)).chunked(7)[1]

    private fun event(id: String, from: Int, to: Int, month: Int = 11) =
        CalendarEvent(id, id, LocalDate(2026, month, from), createdAt = 0, endDate = LocalDate(2026, month, to))

    @Test
    fun aBarSpansItsDays() {
        val bar = weekBars(week, listOf(event("trip", 10, 12))).single()
        assertEquals(2, bar.startCol) // Tuesday
        assertEquals(4, bar.endCol)   // Thursday
        assertEquals(3, bar.span)
        assertEquals(false, bar.continuesBefore)
        assertEquals(false, bar.continuesAfter)
    }

    @Test
    fun aBarCrossingWeeksIsCutAtTheEdges() {
        val bar = weekBars(week, listOf(event("long", 6, 17))).single()
        assertEquals(0, bar.startCol)
        assertEquals(6, bar.endCol)
        assertTrue(bar.continuesBefore && bar.continuesAfter)
    }

    @Test
    fun overlappingBarsGetDifferentRowsAndFreeRowsAreReused() {
        val bars = weekBars(
            week,
            listOf(event("a", 8, 10), event("b", 9, 12), event("c", 11, 13)),
        ).associate { it.event.id to it.lane }
        assertEquals(0, bars["a"])
        assertEquals(1, bars["b"]) // overlaps a
        assertEquals(0, bars["c"]) // a has ended, so c takes the top row again
    }

    @Test
    fun oneDayEventsAreNotBars() {
        val single = CalendarEvent("x", "x", LocalDate(2026, 11, 10), createdAt = 0)
        assertTrue(weekBars(week, listOf(single)).isEmpty())
    }

    @Test
    fun eventsInOtherWeeksAreIgnored() {
        assertTrue(weekBars(week, listOf(event("later", 20, 22))).isEmpty())
    }
}
