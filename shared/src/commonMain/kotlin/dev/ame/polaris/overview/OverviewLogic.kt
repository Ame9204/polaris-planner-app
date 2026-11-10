package dev.ame.polaris.overview

import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.schedule.isHappeningAt
import dev.ame.polaris.util.shortDate
import dev.ame.polaris.util.titleCase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

// Pure logic for the Overview page: no UI, no database, easy to test.

/** How many events and tasks the Overview cards show: the closest few, however far ahead. */
const val GlanceCount = 3

/**
 * The greeting at the top of Overview, by time of day. Each part of the day has a few
 * versions; "%s" is where the name goes. Add your own lines to any list.
 */
private val Greetings: List<Pair<IntRange, List<String>>> = listOf(
    0..3 to listOf(
        "Still up, %s?",
        "Burning the midnight oil, %s?",
        "Night owl mode, %s",
        "The stars are out, %s",
        "Shouldn't you be asleep, %s?",
    ),
    4..5 to listOf(
        "Up before the sun, %s",
        "Early bird, %s",
        "The world's still quiet, %s",
    ),
    6..11 to listOf(
        "Good morning, %s",
        "Rise and shine, %s",
        "Morning, %s. Coffee first",
        "Fresh day, %s",
        "Let's get going, %s",
    ),
    12..13 to listOf(
        "Good afternoon, %s",
        "Lunch o'clock, %s",
        "Halfway there, %s",
    ),
    14..17 to listOf(
        "Good afternoon, %s",
        "Keep it rolling, %s",
        "Afternoon push, %s",
        "Still going strong, %s?",
    ),
    18..20 to listOf(
        "Good evening, %s",
        "Evening, %s",
        "Day's winding down, %s",
    ),
    21..23 to listOf(
        "Winding down, %s?",
        "Almost bedtime, %s",
        "Good evening, %s",
        "Rest up, %s",
    ),
)

/**
 * The greeting for [hour] (0–23). Which version you get depends on [today], so it stays
 * the same all day (no flickering as the screen refreshes) and changes the next day.
 */
fun greetingFor(hour: Int, today: LocalDate, name: String = "Ame"): String {
    val options = Greetings.first { hour in it.first }.second
    val pick = (today.toEpochDays() % options.size).toInt().let { if (it < 0) it + options.size else it }
    return options[pick].replace("%s", name)
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

// ---------- Countdowns ----------

/** Minutes from one moment to another (negative if [toDate]/[toTime] is in the past). */
fun minutesUntil(fromDate: LocalDate, fromTime: LocalTime, toDate: LocalDate, toTime: LocalTime): Long =
    (toDate.toEpochDays() - fromDate.toEpochDays()) * 24 * 60 +
        (toTime.toSecondOfDay() - fromTime.toSecondOfDay()) / 60

/** "now", "in 25 min", "in 2 h 10 min", "tomorrow", "in 3 days" */
fun countdown(minutes: Long): String {
    if (minutes <= 0) return "now"
    if (minutes < 60) return "in $minutes min"
    if (minutes < 24 * 60) {
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0L) "in $h h" else "in $h h $m min"
    }
    val days = minutes / (24 * 60)
    return if (days == 1L) "tomorrow" else "in $days days"
}

/** "45 min left", "1 h 20 min left" (for the block happening now). */
fun timeLeft(now: LocalTime, end: LocalTime): String {
    val minutes = ((end.toSecondOfDay() - now.toSecondOfDay()) / 60).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min left"
        m == 0 -> "$h h left"
        else -> "$h h $m min left"
    }
}

/**
 * How long until an event: "in 25 min", "happening now", "today" (all-day today), "in 3 days".
 * Counted in whole days for all-day events, to the minute for timed ones.
 */
fun eventCountdown(event: CalendarEvent, today: LocalDate, now: LocalTime): String {
    // Already under way (started on an earlier day): say when it ends instead
    if (event.isMultiDay && event.date < today) {
        return if (event.lastDate == today) "ends today" else "until ${shortDate(event.lastDate)}"
    }
    val start = event.start
    if (start == null) {
        val days = event.date.toEpochDays() - today.toEpochDays()
        return when {
            days <= 0L -> "today"
            days == 1L -> "tomorrow"
            else -> "in $days days"
        }
    }
    val minutes = minutesUntil(today, now, event.date, start)
    if (minutes <= 0) return "happening now"
    // A timed event later in the week reads better in days than "in 50 h"
    val dayGap = event.date.toEpochDays() - today.toEpochDays()
    return if (dayGap >= 1 && minutes >= 24 * 60) countdown(dayGap * 24 * 60) else countdown(minutes)
}

/** "due today", "due tomorrow", "due in 3 days", "1 day overdue", "4 days overdue" */
fun dueCountdown(due: LocalDate, today: LocalDate): String {
    val days = due.toEpochDays() - today.toEpochDays()
    return when {
        days == 0L -> "due today"
        days == 1L -> "due tomorrow"
        days > 1 -> "due in $days days"
        days == -1L -> "1 day overdue"
        else -> "${-days} days overdue"
    }
}

// ---------- Today's schedule bar ----------

/**
 * The hours the bar spans: at least 06:00–22:00, stretched to fit earlier or later blocks.
 * Returns (first hour, last hour), e.g. (6, 22).
 */
fun barHours(blocks: List<ScheduleBlock>): Pair<Int, Int> {
    val first = blocks.minOfOrNull { it.start.hour } ?: 6
    val last = blocks.maxOfOrNull { if (it.end.minute > 0) it.end.hour + 1 else it.end.hour } ?: 22
    return minOf(6, first) to maxOf(22, minOf(24, last))
}

/** Where [time] falls along the bar, from 0.0 (left end) to 1.0 (right end). */
fun barFraction(time: LocalTime, hours: Pair<Int, Int>): Float {
    val (from, to) = hours
    val minute = time.toSecondOfDay() / 60f
    return ((minute - from * 60) / ((to - from) * 60)).coerceIn(0f, 1f)
}

// ---------- When there's nothing next ----------

/** Shown in the Next box when nothing else is on today. One per hour, so it changes during the day. */
val IdleLines = listOf(
    "Nothing next. Touch grass.",
    "Zero obligations detected.",
    "Clear skies ahead.",
    "The rest of today is yours.",
    "System idle. Carry on.",
    "No plans. Main character energy.",
    "Free as a bird.",
    "Nothing queued. Go wander.",
)

/** The idle line for this hour of this day (the same all hour, a different one the next). */
fun idleLine(today: LocalDate, hour: Int): String {
    val slot = today.toEpochDays() * 24 + hour
    return IdleLines[(slot % IdleLines.size).toInt().let { if (it < 0) it + IdleLines.size else it }]
}

// ---------- Big numbers ----------

/** The three counts in Overview's big-number row. */
data class TodayStats(val tasksDue: Int, val eventsToday: Int, val blocksLeft: Int)

/**
 * Tasks due today or overdue, events happening today (multi-day ones included),
 * and schedule blocks that haven't finished yet.
 */
fun todayStats(
    tasks: List<dev.ame.polaris.tasks.Task>,
    events: List<CalendarEvent>,
    blocks: List<ScheduleBlock>,
    today: LocalDate,
    now: LocalTime,
): TodayStats = TodayStats(
    tasksDue = tasks.count { !it.done && it.due != null && it.due <= today },
    eventsToday = events.count { it.occursOn(today) },
    blocksLeft = blocks.count { it.end > now },
)
