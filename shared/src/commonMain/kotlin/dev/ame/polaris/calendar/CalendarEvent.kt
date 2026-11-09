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
) {
    val isAllDay: Boolean get() = start == null
}
