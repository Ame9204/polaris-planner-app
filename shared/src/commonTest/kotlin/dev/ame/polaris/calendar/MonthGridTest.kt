package dev.ame.polaris.calendar

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthGridTest {

    @Test
    fun gridStartsOnTheSundayBeforeTheFirst() {
        // 1 Dec 2026 is a Tuesday, so the grid starts on Sunday 29 Nov.
        val days = monthGrid(LocalDate(2026, 12, 1))
        assertEquals(LocalDate(2026, 11, 29), days.first())
        assertEquals(DayOfWeek.SUNDAY, days.first().dayOfWeek)
    }

    @Test
    fun gridIsAlwaysSixFullWeeks() {
        val days = monthGrid(LocalDate(2026, 2, 14))
        assertEquals(42, days.size)
        assertTrue(LocalDate(2026, 2, 1) in days && LocalDate(2026, 2, 28) in days)
    }

    @Test
    fun monthStartingOnSundayHasNoLeadingDays() {
        // 1 Nov 2026 is a Sunday.
        assertEquals(LocalDate(2026, 11, 1), monthGrid(LocalDate(2026, 11, 20)).first())
    }

    @Test
    fun titleIsReadable() {
        assertEquals("November 2026", monthTitle(LocalDate(2026, 11, 9)))
    }
}

class RangeLabelTest {
    @Test
    fun labels() {
        assertEquals("9 Nov", dev.ame.polaris.ui.rangeLabel(LocalDate(2026, 11, 9), LocalDate(2026, 11, 9)))
        assertEquals("9 – 11 Nov", dev.ame.polaris.ui.rangeLabel(LocalDate(2026, 11, 9), LocalDate(2026, 11, 11)))
        assertEquals("30 Nov – 2 Dec", dev.ame.polaris.ui.rangeLabel(LocalDate(2026, 11, 30), LocalDate(2026, 12, 2)))
    }
}
