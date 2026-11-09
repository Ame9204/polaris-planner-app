package dev.ame.polaris.calendar

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * One calendar event, and one row in the `events` table.
 * No [start] time means it's an all-day event.
 */
@Entity(tableName = "events")
data class CalendarEvent(
    @PrimaryKey val id: String,
    val title: String,
    val date: LocalDate,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val createdAt: Long,
    /**
     * The last day, for events spanning several days (like Google Calendar's "ends on").
     * Null means it ends on the same day it starts. A timed multi-day event starts at
     * [start] on [date] and ends at [end] on this day.
     */
    val endDate: LocalDate? = null,
) {
    val isAllDay: Boolean get() = start == null

    /** The day it ends (the same as [date] for one-day events). */
    val lastDate: LocalDate get() = endDate ?: date

    val isMultiDay: Boolean get() = lastDate > date

    /** Does this event cover [day]? */
    fun occursOn(day: LocalDate): Boolean = day >= date && day <= lastDate
}
