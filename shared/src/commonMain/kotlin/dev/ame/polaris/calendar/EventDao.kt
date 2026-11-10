package dev.ame.polaris.calendar

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.ame.polaris.util.nowMillis
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
    @Query("SELECT * FROM events WHERE deleted = 0 AND date <= :to AND COALESCE(endDate, date) >= :from ORDER BY date, start, createdAt")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>>

    /** The next [limit] events that haven't ended before [from], soonest first, however far ahead. */
    @Query("SELECT * FROM events WHERE deleted = 0 AND COALESCE(endDate, date) >= :from ORDER BY date, start, createdAt LIMIT :limit")
    fun observeUpcoming(from: LocalDate, limit: Int): Flow<List<CalendarEvent>>


    // Writes go through the plain-named functions (upsert, delete, toggle…), which stamp
    // `changedAt` with the current time so sync knows what changed. The "…At"/"Raw" ones are
    // what Room actually runs; the sync engine uses upsertRaw to store rows from the cloud
    // exactly as they are.

    suspend fun upsert(event: CalendarEvent) = upsertRaw(event.copy(changedAt = nowMillis(), deleted = false))

    @Upsert
    suspend fun upsertRaw(event: CalendarEvent)

    suspend fun delete(id: String) = markDeleted(id, nowMillis())

    @Query("UPDATE events SET deleted = 1, changedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    /** Sync: every row changed after [since], deleted ones included. */
    @Query("SELECT * FROM events WHERE changedAt > :since")
    suspend fun changedSince(since: Long): List<CalendarEvent>

    /** Sync: one row by id, deleted or not (to compare with what the cloud sent). */
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun findById(id: String): CalendarEvent?
}
