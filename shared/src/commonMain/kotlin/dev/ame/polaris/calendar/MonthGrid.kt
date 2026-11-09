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
