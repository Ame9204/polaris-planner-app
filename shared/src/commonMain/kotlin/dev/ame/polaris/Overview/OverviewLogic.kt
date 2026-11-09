package dev.ame.polaris.overview

import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.schedule.isHappeningAt
import dev.ame.polaris.util.shortDate
import dev.ame.polaris.util.titleCase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// Pure logic for the Overview page: no UI, no database, easy to test.

/** How many days ahead "upcoming" looks, counting today. */
const val UpcomingDays = 7

fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good Morning"
    in 12..17 -> "Good Afternoon"
    else -> "Good Evening"
}

/** The block happening now (if any) and the next one to start today (if any). */
data class NowAndNext(val now: ScheduleBlock?, val next: ScheduleBlock?)

fun nowAndNext(blocks: List<ScheduleBlock>, time: LocalTime): NowAndNext =
    NowAndNext(
        now = blocks.firstOrNull { it.isHappeningAt(time) },
        next = blocks.filter { it.start > time }.minByOrNull { it.start },
    )

/**
 * Events worth showing: everything in the coming days, minus today's timed events
 * that are already over. All-day events stay for the whole day.
 */
fun upcomingEvents(events: List<CalendarEvent>, today: LocalDate, time: LocalTime): List<CalendarEvent> =
    events.filterNot { event ->
        val finished = event.end ?: event.start
        event.date == today && finished != null && finished <= time
    }

/** "Today", "Tomorrow", "Yesterday", or e.g. "Thu 12 Nov". */
fun dayLabel(date: LocalDate, today: LocalDate): String =
    when (date.toEpochDays() - today.toEpochDays()) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> "${date.dayOfWeek.name.take(3).titleCase()} ${shortDate(date)}"
    }
