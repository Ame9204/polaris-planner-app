package dev.ame.polaris.schedule

import dev.ame.polaris.util.titleCase
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

// Pure logic for the Schedule page: no UI, no database, easy to test.

// ---------- Repeat days <-> bits ----------

/** Monday = 1, Tuesday = 2, Wednesday = 4 ... Sunday = 64. */
fun DayOfWeek.bit(): Int = 1 shl (isoDayNumber - 1)

fun Set<DayOfWeek>.toBits(): Int = fold(0) { bits, day -> bits or day.bit() }

fun daysFromBits(bits: Int): Set<DayOfWeek> =
    DayOfWeek.entries.filter { bits and it.bit() != 0 }.toSet()

val AllDays: Set<DayOfWeek> = DayOfWeek.entries.toSet()
val Weekdays: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)
val Weekend: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** "Once", "Every day", "Weekdays", "Weekends", or e.g. "Mon, Wed, Fri". */
fun repeatLabel(bits: Int): String {
    val days = daysFromBits(bits)
    return when (days) {
        emptySet<DayOfWeek>() -> "Once"
        AllDays -> "Every day"
        Weekdays -> "Weekdays"
        Weekend -> "Weekends"
        else -> days.sortedBy { it.isoDayNumber }.joinToString { it.shortName() }
    }
}

/** "Mon" */
fun DayOfWeek.shortName(): String = name.take(3).titleCase()

// ---------- Times ----------

fun minutesBetween(from: LocalTime, to: LocalTime): Int =
    (to.toSecondOfDay() - from.toSecondOfDay()) / 60

/** "45 min", "1 h", "1 h 30 min" */
fun durationLabel(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h $m min"
    }
}

/** One hour later, but never past the end of the day. */
fun LocalTime.oneHourLater(): LocalTime =
    if (hour >= 23) LocalTime(23, 59) else LocalTime(hour + 1, minute)

// ---------- The day's timeline ----------

/** One line on the Schedule page: either a block, or free time between blocks. */
sealed interface TimelineItem {
    data class Block(val block: ScheduleBlock) : TimelineItem
    data class Free(val start: LocalTime, val end: LocalTime) : TimelineItem {
        val minutes: Int get() = minutesBetween(start, end)
    }
}

/**
 * The day's blocks in time order, with "free" gaps of at least [minFreeMinutes]
 * between them. Overlapping blocks are fine: a gap only starts after every earlier block has ended.
 */
fun timeline(blocks: List<ScheduleBlock>, minFreeMinutes: Int = 15): List<TimelineItem> {
    val sorted = blocks.sortedWith(compareBy({ it.start }, { it.createdAt }))
    val items = mutableListOf<TimelineItem>()
    var busyUntil: LocalTime? = null
    for (block in sorted) {
        val gapStart = busyUntil
        if (gapStart != null && minutesBetween(gapStart, block.start) >= minFreeMinutes) {
            items += TimelineItem.Free(gapStart, block.start)
        }
        items += TimelineItem.Block(block)
        busyUntil = if (gapStart == null || block.end > gapStart) block.end else gapStart
    }
    return items
}

/** Is [now] inside this block? (Start included, end excluded.) */
fun ScheduleBlock.isHappeningAt(now: LocalTime): Boolean = now >= start && now < end
