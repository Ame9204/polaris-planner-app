package dev.ame.polaris.schedule

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
 * A block plus where it goes on the timeline. Overlapping blocks share the width:
 * [laneCount] columns side by side, and this block sits in column [lane] (0 = left).
 */
data class PlacedBlock(val block: ScheduleBlock, val lane: Int, val laneCount: Int)

/**
 * Splits overlapping blocks into side-by-side lanes, like a calendar app's day view.
 * Blocks that don't overlap anything get the full width. Each block takes the leftmost
 * lane that's free when it starts; every block in an overlapping group gets the same
 * lane count, so their widths line up.
 */
fun layoutLanes(blocks: List<ScheduleBlock>): List<PlacedBlock> {
    val sorted = blocks.sortedWith(compareBy({ it.start }, { it.createdAt }))
    val result = mutableListOf<PlacedBlock>()

    val group = mutableListOf<Pair<ScheduleBlock, Int>>() // blocks overlapping each other, with their lane
    val laneEnds = mutableListOf<LocalTime>()               // when each lane in the group frees up
    var groupEnd: LocalTime? = null

    fun closeGroup() {
        group.forEach { (block, lane) -> result += PlacedBlock(block, lane, laneEnds.size) }
        group.clear()
        laneEnds.clear()
    }

    for (block in sorted) {
        val end = groupEnd
        if (end != null && block.start >= end) {
            closeGroup() // nothing still running: start a new group
            groupEnd = null
        }
        var lane = laneEnds.indexOfFirst { it <= block.start }
        if (lane == -1) {
            laneEnds += block.end
            lane = laneEnds.lastIndex
        } else {
            laneEnds[lane] = block.end
        }
        group += block to lane
        val current = groupEnd
        groupEnd = if (current == null || block.end > current) block.end else current
    }
    closeGroup()
    return result
}

/** "12 AM", "1 AM" … "12 PM", "1 PM" … "11 PM" */
fun hourLabel(hour: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$h12 ${if (hour < 12) "AM" else "PM"}"
}

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
