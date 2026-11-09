package dev.ame.polaris.reminders

import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.tasks.Task
import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.shortDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * When Polaris reminds you of things. Change these to taste; everything else follows.
 * Set a time to null (or minutes to null) to turn that kind of reminder off.
 */
data class ReminderRules(
    /** Timed events: this many minutes before they start. */
    val eventMinutesBefore: Int? = 10,
    /** All-day events: at this time on the day. */
    val allDayEventsAt: LocalTime? = LocalTime(8, 0),
    /** Unfinished tasks without their own reminder: at this time on their due date. */
    val tasksDueAt: LocalTime? = LocalTime(9, 0),
    /** Schedule blocks: as set on each block (at start, some minutes before, or off). False = never. */
    val scheduleBlocks: Boolean = true,
) {
    companion object {
        val Default = ReminderRules()
    }
}

/** One notification to show at [at]. [key] identifies it, so the same thing is never shown twice. */
data class Reminder(
    val key: String,
    val at: LocalDateTime,
    val title: String,
    val text: String,
)

/**
 * Every reminder for the days [from]..[to], from the data on those days.
 * Pure function: no clock, no database, so it's easy to test.
 */
fun planReminders(
    from: LocalDate,
    to: LocalDate,
    events: List<CalendarEvent>,
    tasks: List<Task>,
    blocksByDay: Map<LocalDate, List<ScheduleBlock>>,
    rules: ReminderRules = ReminderRules.Default,
): List<Reminder> {
    val out = mutableListOf<Reminder>()
    val days = from..to

    for (event in events) {
        if (event.date !in days) continue
        val start = event.start
        if (start == null) {
            val at = rules.allDayEventsAt ?: continue
            out += Reminder("event:${event.id}", event.date.atTime(at), event.title, "Today · all day")
        } else {
            val before = rules.eventMinutesBefore ?: continue
            out += Reminder(
                "event:${event.id}",
                event.date.atTime(start).minusMinutes(before),
                event.title,
                if (before == 0) "Starting now" else "Starts at ${hhmm(start)} (in $before min)",
            )
        }
    }

    for (task in tasks) {
        if (task.done) continue
        val own = task.remindAt
        if (own != null) {
            // The task's own reminder replaces the default due-date one.
            if (own.date !in days) continue
            out += Reminder(
                "task:${task.id}",
                own,
                task.title,
                task.due?.let { "Task · due ${shortDate(it)}" } ?: "Task reminder",
            )
        } else {
            val due = task.due ?: continue
            val at = rules.tasksDueAt ?: continue
            if (due !in days) continue
            out += Reminder("task:${task.id}", due.atTime(at), "Task due today", task.title)
        }
    }

    if (rules.scheduleBlocks) {
        for ((day, blocks) in blocksByDay) {
            if (day !in days) continue
            for (block in blocks) {
                val before = block.remindMinutesBefore ?: continue // reminder off for this block
                // Repeating blocks happen on many days, so the day is part of the key.
                out += Reminder(
                    "block:${block.id}:$day",
                    day.atTime(block.start).minusMinutes(before),
                    block.title,
                    if (before == 0) "Now until ${hhmm(block.end)}" else "Starts at ${hhmm(block.start)} (in $before min)",
                )
            }
        }
    }

    return out.sortedBy { it.at }
}

/** Works across midnight: 00:05 minus 10 minutes is 23:55 the day before. */
private fun LocalDateTime.minusMinutes(minutes: Int): LocalDateTime {
    val zone = TimeZone.UTC // any fixed zone works for plain clock arithmetic
    return toInstant(zone).minus(minutes, DateTimeUnit.MINUTE, zone).toLocalDateTime(zone)
}

/** Every day from [from] to [to], inclusive. */
fun daysBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
    generateSequence(from) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= to }.toList()
