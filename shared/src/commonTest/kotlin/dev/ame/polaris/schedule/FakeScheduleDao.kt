package dev.ame.polaris.schedule

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** An in-memory stand-in for the schedule_blocks table, with the same day rule as the SQL. */
class FakeScheduleDao : ScheduleDao {
    val items = MutableStateFlow<List<ScheduleBlock>>(emptyList())

    override fun observeForDayRaw(date: LocalDate, dayBit: Int): Flow<List<ScheduleBlock>> =
        items.map { list ->
            list.filter { it.date == date || (it.days and dayBit) != 0 }
                .sortedWith(compareBy({ it.start }, { it.createdAt }))
        }

    override fun observeForRangeRaw(from: LocalDate, to: LocalDate): Flow<List<ScheduleBlock>> =
        items.map { list ->
            list.filter { it.days != 0 || (it.date != null && it.date in from..to) }
                .sortedWith(compareBy({ it.start }, { it.createdAt }))
        }

    override suspend fun upsertRaw(block: ScheduleBlock) {
        items.update { list -> list.filterNot { it.id == block.id } + block }
    }

    override suspend fun markDeleted(id: String, now: Long) {
        items.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun changedSince(since: Long): List<ScheduleBlock> = items.value.filter { it.changedAt > since }

    override suspend fun findById(id: String): ScheduleBlock? = items.value.firstOrNull { it.id == id }
}
