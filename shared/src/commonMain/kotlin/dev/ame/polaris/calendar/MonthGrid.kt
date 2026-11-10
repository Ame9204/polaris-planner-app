package dev.ame.polaris.calendar

import dev.ame.polaris.util.WeekOrder
import dev.ame.polaris.util.startOfWeek
import dev.ame.polaris.util.titleCase
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

// Pure date math for the month view: no UI, no database, easy to test.

/** The 1st of this date's month. Used to represent "a month". */
fun LocalDate.firstOfMonth(): LocalDate = LocalDate(year, month, 1)

/**
 * The 42 days (6 weeks, starting on the app's first day of the week) shown in the grid for [month].
 * Always 6 rows, so the grid doesn't jump in height between months.
 */
fun monthGrid(month: LocalDate): List<LocalDate> {
    val first = month.firstOfMonth()
    val start = first.startOfWeek()
    return List(42) { start.plus(it, DateTimeUnit.DAY) }
}

/** "November 2026" */
fun monthTitle(month: LocalDate): String = "${month.month.name.titleCase()} ${month.year}"

/** "Sun", "Mon" … in the app's week order (see util/Week.kt). */
val WeekdayLabels: List<String> = WeekOrder.map { it.name.take(3).titleCase() }

// ---------- Multi-day events as continuous bars ----------

/**
 * One multi-day event's bar within one week row of the grid: from column [startCol] to
 * [endCol] (0–6), in row [lane] (0 = top). [continuesBefore]/[continuesAfter] say the
 * event carries on from the previous week / into the next, so that end is drawn square.
 */
data class WeekBar(
    val event: CalendarEvent,
    val startCol: Int,
    val endCol: Int,
    val lane: Int,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
) {
    val span: Int get() = endCol - startCol + 1
}

/**
 * Lays out the multi-day events that touch [week] (7 days), like Google Calendar's month view:
 * earlier and longer events take the top rows, and each bar takes the highest row that's
 * free across all its days, so bars never overlap.
 */
fun weekBars(week: List<LocalDate>, events: List<CalendarEvent>): List<WeekBar> {
    val first = week.first()
    val last = week.last()
    val touching = events
        .filter { it.isMultiDay && it.date <= last && it.lastDate >= first }
        .distinctBy { it.id }
        .sortedWith(compareBy<CalendarEvent>({ it.date }, { -(it.lastDate.toEpochDays() - it.date.toEpochDays()) }, { it.createdAt }))
    val laneEnds = mutableListOf<Int>() // last column used in each row
    return touching.map { event ->
        val startCol = if (event.date < first) 0 else week.indexOf(event.date)
        val endCol = if (event.lastDate > last) 6 else week.indexOf(event.lastDate)
        var lane = laneEnds.indexOfFirst { it < startCol }
        if (lane == -1) {
            laneEnds += endCol
            lane = laneEnds.lastIndex
        } else {
            laneEnds[lane] = endCol
        }
        WeekBar(event, startCol, endCol, lane, continuesBefore = (event.date < first), continuesAfter = (event.lastDate > last))
    }
}
