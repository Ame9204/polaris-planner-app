package dev.ame.polaris.schedule

import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.util.shortDate
import dev.ame.polaris.util.titleCase
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

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

// ---------- Weeks ----------

/** Does this block happen on [day]? (A one-off on that date, or repeating on that weekday.) */
fun ScheduleBlock.happensOn(day: LocalDate): Boolean =
    date == day || (days and day.dayOfWeek.bit()) != 0

/** The Monday-to-Sunday week that contains [date]. */
fun weekOf(date: LocalDate): List<LocalDate> {
    val monday = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    return List(7) { monday.plus(it, DateTimeUnit.DAY) }
}

/** "9 – 15 Nov", or "30 Nov – 6 Dec" when the week crosses months. */
fun weekLabel(week: List<LocalDate>): String {
    val first = week.first()
    val last = week.last()
    return if (first.month == last.month) {
        "${first.day} – ${shortDate(last)}"
    } else {
        "${shortDate(first)} – ${shortDate(last)}"
    }
}

// ---------- Drawing the day as a timeline ----------

/**
 * Something drawn on the timeline: one of your blocks, or a timed calendar event.
 * Both have a start and end, so they share one layout and sit side by side when they overlap.
 */
sealed interface TimelineEntry {
    val start: LocalTime
    val end: LocalTime
    val createdAt: Long

    data class Block(val block: ScheduleBlock) : TimelineEntry {
        override val start get() = block.start
        override val end get() = block.end
        override val createdAt get() = block.createdAt
    }

    /** Only timed events go on the timeline; all-day ones go in the strip above it. */
    data class Event(val event: CalendarEvent) : TimelineEntry {
        override val start get() = event.start ?: LocalTime(0, 0)
        override val end get() = eventEnd(event)
        override val createdAt get() = event.createdAt
    }
}

/** Events without an end time are drawn 30 minutes long (but never past midnight). */
fun eventEnd(event: CalendarEvent): LocalTime {
    event.end?.let { return it }
    val start = event.start ?: return LocalTime(23, 59)
    val seconds = minOf(start.toSecondOfDay() + 30 * 60, 24 * 3600 - 60)
    return LocalTime.fromSecondOfDay(seconds)
}

/** A day's blocks plus its timed events, ready to lay out. */
fun timelineEntries(blocks: List<ScheduleBlock>, events: List<CalendarEvent>): List<TimelineEntry> =
    blocks.map { TimelineEntry.Block(it) } + events.filter { it.start != null }.map { TimelineEntry.Event(it) }

/**
 * An entry plus where it goes. Overlapping entries share the width:
 * [laneCount] columns side by side, and this one sits in column [lane] (0 = left).
 */
data class Placed<T>(val item: T, val lane: Int, val laneCount: Int)

/**
 * Splits overlapping entries into side-by-side lanes, like a calendar app's day view.
 * Entries that don't overlap anything get the full width. Each one takes the leftmost
 * lane that's free when it starts; every entry in an overlapping group gets the same
 * lane count, so their widths line up.
 */
fun layoutEntries(entries: List<TimelineEntry>): List<Placed<TimelineEntry>> {
    val sorted = entries.sortedWith(compareBy({ it.start }, { it.createdAt }))
    val result = mutableListOf<Placed<TimelineEntry>>()

    val group = mutableListOf<Pair<TimelineEntry, Int>>() // entries overlapping each other, with their lane
    val laneEnds = mutableListOf<LocalTime>()              // when each lane in the group frees up
    var groupEnd: LocalTime? = null

    fun closeGroup() {
        group.forEach { (entry, lane) -> result += Placed(entry, lane, laneEnds.size) }
        group.clear()
        laneEnds.clear()
    }

    for (entry in sorted) {
        val end = groupEnd
        if (end != null && entry.start >= end) {
            closeGroup() // nothing still running: start a new group
            groupEnd = null
        }
        var lane = laneEnds.indexOfFirst { it <= entry.start }
        if (lane == -1) {
            laneEnds += entry.end
            lane = laneEnds.lastIndex
        } else {
            laneEnds[lane] = entry.end
        }
        group += entry to lane
        val current = groupEnd
        groupEnd = if (current == null || entry.end > current) entry.end else current
    }
    closeGroup()
    return result
}

/** Blocks only (kept for the tests and anything that only has blocks). */
data class PlacedBlock(val block: ScheduleBlock, val lane: Int, val laneCount: Int)

fun layoutLanes(blocks: List<ScheduleBlock>): List<PlacedBlock> =
    layoutEntries(blocks.map { TimelineEntry.Block(it) })
        .map { PlacedBlock((it.item as TimelineEntry.Block).block, it.lane, it.laneCount) }

/** "00:00", "01:00" … "23:00" (24-hour, like every other time in the app). */
fun hourLabel(hour: Int): String = "${hour.toString().padStart(2, '0')}:00"

fun minuteOfDay(time: LocalTime): Int = time.toSecondOfDay() / 60

/**
 * The time at [offsetPx] down the timeline (where [hourPx] is one hour's height),
 * rounded down to [snapMinutes]. Used when you tap an empty spot to add a block.
 */
fun timeAtOffset(offsetPx: Float, hourPx: Float, snapMinutes: Int = 30): LocalTime {
    val minutes = (offsetPx / hourPx * 60).toInt()
    val snapped = (minutes / snapMinutes * snapMinutes).coerceIn(0, 24 * 60 - snapMinutes)
    return LocalTime(snapped / 60, snapped % 60)
}

/** Is [now] inside this block? (Start included, end excluded.) */
fun ScheduleBlock.isHappeningAt(now: LocalTime): Boolean = now >= start && now < end
