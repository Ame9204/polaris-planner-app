package dev.ame.polaris.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ame.polaris.util.newId
import dev.ame.polaris.util.nowMillis
import dev.ame.polaris.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

data class ScheduleUiState(
    val date: LocalDate,
    val today: LocalDate,
    val blocks: List<ScheduleBlock>,
) {
    val items: List<TimelineItem> get() = timeline(blocks)
    val isToday: Boolean get() = date == today
}

class ScheduleViewModel(
    private val dao: ScheduleDao,
    private val today: LocalDate = today(),
) : ViewModel() {

    private val date = MutableStateFlow(today)

    /** Re-queries the database whenever you move to another day. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ScheduleUiState> = date
        .flatMapLatest { d ->
            dao.observeForDay(d, d.dayOfWeek.bit()).map { ScheduleUiState(d, today, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState(today, today, emptyList()))

    fun previousDay() = date.update { it.minus(1, DateTimeUnit.DAY) }

    fun nextDay() = date.update { it.plus(1, DateTimeUnit.DAY) }

    fun goToToday() {
        date.value = today
    }

    /**
     * Creates a block, or updates [existing] if given.
     * No repeat days = a one-off on the day being viewed (or the day it was already on).
     */
    fun save(
        existing: ScheduleBlock?,
        title: String,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
    ) {
        val clean = title.trim()
        if (clean.isEmpty() || end <= start) return
        val bits = days.toBits()
        val block = ScheduleBlock(
            id = existing?.id ?: newId(),
            title = clean,
            start = start,
            end = end,
            days = bits,
            date = if (bits == 0) existing?.date ?: date.value else null,
            createdAt = existing?.createdAt ?: nowMillis(),
        )
        viewModelScope.launch { dao.upsert(block) }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.delete(id) }
    }
}
