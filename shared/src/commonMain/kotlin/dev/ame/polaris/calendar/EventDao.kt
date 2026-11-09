package dev.ame.polaris.calendar

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface EventDao {

    /**
     * Events from [from] to [to], inclusive. Dates are stored as "2026-11-09" text,
     * which sorts the same way as the dates themselves, so BETWEEN just works.
     * All-day events (no start time, NULL) sort first in each day.
     */
    @Query("SELECT * FROM events WHERE date BETWEEN :from AND :to ORDER BY date, start, createdAt")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>>

    @Upsert
    suspend fun upsert(event: CalendarEvent)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: String)
}
