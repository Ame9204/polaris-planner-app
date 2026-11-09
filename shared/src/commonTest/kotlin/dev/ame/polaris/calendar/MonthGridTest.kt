package dev.ame.polaris.calendar

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthGridTest {

    @Test
    fun gridStartsOnTheMondayBeforeTheFirst() {
        // 1 Nov 2026 is a Sunday, so the grid starts on Monday 26 Oct.
        val days = monthGrid(LocalDate(2026, 11, 1))
        assertEquals(LocalDate(2026, 10, 26), days.first())
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
    }

    @Test
    fun gridIsAlwaysSixFullWeeks() {
        val days = monthGrid(LocalDate(2026, 2, 14))
        assertEquals(42, days.size)
        assertTrue(LocalDate(2026, 2, 1) in days && LocalDate(2026, 2, 28) in days)
    }

    @Test
    fun monthStartingOnMondayHasNoLeadingDays() {
        // 1 June 2026 is a Monday.
        assertEquals(LocalDate(2026, 6, 1), monthGrid(LocalDate(2026, 6, 20)).first())
    }

    @Test
    fun titleIsReadable() {
        assertEquals("November 2026", monthTitle(LocalDate(2026, 11, 9)))
    }
}
