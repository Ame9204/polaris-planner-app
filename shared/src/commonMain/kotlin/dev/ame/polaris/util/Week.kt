package dev.ame.polaris.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/**
 * Which day weeks start on, everywhere in the app: the Calendar grid, the Schedule's
 * Week view, and the weekday chips. Change it to DayOfWeek.MONDAY to switch back.
 */
val FirstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY

/** The seven days in display order, starting with [FirstDayOfWeek]. */
val WeekOrder: List<DayOfWeek> = List(7) { DayOfWeek((FirstDayOfWeek.isoDayNumber - 1 + it) % 7 + 1) }

/** How many days this date is into its week (0 = the first day). */
val LocalDate.dayIndexInWeek: Int
    get() = (dayOfWeek.isoDayNumber - FirstDayOfWeek.isoDayNumber + 7) % 7

/** The first day of the week this date is in. */
fun LocalDate.startOfWeek(): LocalDate = minus(dayIndexInWeek, DateTimeUnit.DAY)
