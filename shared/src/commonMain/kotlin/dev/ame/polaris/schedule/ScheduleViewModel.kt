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
import kotlinx.coroutines.flow.combine
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

enum class ScheduleView { Day, Week }

data class ScheduleUiState(
    /** The day being shown (Day view), or any day in the week being shown (Week view). */
    val date: LocalDate,
    val today: LocalDate,
    val view: ScheduleView = ScheduleView.Day,
    /** Each shown day with its blocks: one day in Day view, Monday to Sunday in Week view. */
    val days: List<Pair<LocalDate, List<ScheduleBlock>>> = listOf(date to emptyList()),
) {
    val blocks: List<ScheduleBlock> get() = days.firstOrNull { it.first == date }?.second.orEmpty()
    val items: List<TimelineItem> get() = timeline(blocks)
    val isToday: Boolean get() = date == today
}

class ScheduleViewModel(
    private val dao: ScheduleDao,
    private val today: LocalDate = today(),
) : ViewModel() {

    private val date = MutableStateFlow(today)
    private val view = MutableStateFlow(ScheduleView.Day)

    /**
     * Re-queries whenever the day or the view changes.
     * Day: just that day's blocks. Week: one query for the whole week, then sorted into days.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ScheduleUiState> = combine(date, view) { d, v -> d to v }
        .flatMapLatest { (d, v) ->
            when (v) {
                ScheduleView.Day ->
                    dao.observeForDay(d, d.dayOfWeek.bit())
                        .map { ScheduleUiState(d, today, v, listOf(d to it)) }
                ScheduleView.Week -> {
                    val week = weekOf(d)
                    dao.observeForRange(week.first(), week.last()).map { all ->
                        ScheduleUiState(d, today, v, week.map { day -> day to all.filter { it.happensOn(day) } })
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState(today, today))

    fun setView(newView: ScheduleView) {
        view.value = newView
    }

    /** Open one day in Day view (e.g. after tapping its name in the week). */
    fun openDay(day: LocalDate) {
        date.value = day
        view.value = ScheduleView.Day
    }

    /** ‹: one day back in Day view, one week back in Week view. */
    fun previous() = date.update { it.minus(step(), DateTimeUnit.DAY) }

    fun next() = date.update { it.plus(step(), DateTimeUnit.DAY) }

    private fun step() = if (view.value == ScheduleView.Week) 7 else 1

    fun goToToday() {
        date.value = today
    }

    /**
     * Creates a block, or updates [existing] if given.
     * No repeat days = a one-off on [onDate] (the day you tapped), or the day being viewed.
     */
    fun save(
        existing: ScheduleBlock?,
        title: String,
        start: LocalTime,
        end: LocalTime,
        days: Set<DayOfWeek>,
        onDate: LocalDate? = null,
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
            date = if (bits == 0) existing?.date ?: onDate ?: date.value else null,
            createdAt = existing?.createdAt ?: nowMillis(),
        )
        viewModelScope.launch { dao.upsert(block) }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.delete(id) }
    }
}
