package dev.ame.polaris.calendar

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface EventDao {

    /**
     * Events that cover any day from [from] to [to], inclusive, including multi-day events
     * that started earlier (a 3-day trip that began yesterday still covers today).
     * COALESCE(endDate, date) is the last day, since endDate is empty for one-day events.
     * Dates are stored as "2026-11-09" text, which sorts like the dates themselves.
     * All-day events (no start time, NULL) sort first in each day.
     */
    @Query("SELECT * FROM events WHERE date <= :to AND COALESCE(endDate, date) >= :from ORDER BY date, start, createdAt")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>>

    /** The next [limit] events that haven't ended before [from], soonest first, however far ahead. */
    @Query("SELECT * FROM events WHERE COALESCE(endDate, date) >= :from ORDER BY date, start, createdAt LIMIT :limit")
    fun observeUpcoming(from: LocalDate, limit: Int): Flow<List<CalendarEvent>>

    @Upsert
    suspend fun upsert(event: CalendarEvent)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: String)
}
