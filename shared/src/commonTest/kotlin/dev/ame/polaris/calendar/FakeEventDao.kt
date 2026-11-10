package dev.ame.polaris.calendar

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** An in-memory stand-in for the events table. */
class FakeEventDao : EventDao {
    val items = MutableStateFlow<List<CalendarEvent>>(emptyList())

    override fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> =
        items.map { list -> list.filter { it.date <= to && it.lastDate >= from } }

    override fun observeUpcoming(from: LocalDate, limit: Int): Flow<List<CalendarEvent>> =
        items.map { list ->
            list.filter { it.lastDate >= from }
                .sortedWith(compareBy<CalendarEvent>({ it.date }, { it.start }, { it.createdAt }))
                .take(limit)
        }

    override suspend fun upsertRaw(event: CalendarEvent) {
        items.update { list -> list.filterNot { it.id == event.id } + event }
    }

    override suspend fun markDeleted(id: String, now: Long) {
        items.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun changedSince(since: Long): List<CalendarEvent> = items.value.filter { it.changedAt > since }

    override suspend fun findById(id: String): CalendarEvent? = items.value.firstOrNull { it.id == id }
}
