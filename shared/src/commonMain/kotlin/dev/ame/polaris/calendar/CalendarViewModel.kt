package dev.ame.polaris.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ame.polaris.tasks.Task
import dev.ame.polaris.tasks.TaskDao
import dev.ame.polaris.util.newId
import dev.ame.polaris.util.nowMillis
import dev.ame.polaris.util.today
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Everything the Calendar screen needs to draw itself, in one object. */
data class CalendarUiState(
    val month: LocalDate,                       // 1st of the month being shown
    val selected: LocalDate,                    // the day whose details are shown
    val today: LocalDate,
    val days: List<LocalDate>,                  // the 42 grid days
    val events: Map<LocalDate, List<CalendarEvent>>,
    val tasks: Map<LocalDate, List<Task>>,      // only tasks that have a due date
) {
    val selectedEvents: List<CalendarEvent> get() = events[selected].orEmpty()
    val selectedTasks: List<Task> get() = tasks[selected].orEmpty()
}

class CalendarViewModel(
    private val eventDao: EventDao,
    private val taskDao: TaskDao,
    private val today: LocalDate = today(), // a parameter so tests can pick "today"
) : ViewModel() {

    private val month = MutableStateFlow(today.firstOfMonth())
    private val selected = MutableStateFlow(today)

    /**
     * Whenever the month changes, flatMapLatest drops the old database queries and starts
     * new ones for the new grid. combine merges events, tasks and the selected day into one state.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CalendarUiState> = month
        .flatMapLatest { m ->
            val days = monthGrid(m)
            combine(
                eventDao.observeBetween(days.first(), days.last()),
                taskDao.observeDueBetween(days.first(), days.last()),
                selected,
            ) { events, tasks, sel ->
                CalendarUiState(
                    month = m,
                    selected = sel,
                    today = today,
                    days = days,
                    // A multi-day event is listed under every day it covers
                    events = days.associateWith { day -> events.filter { it.occursOn(day) } }.filterValues { it.isNotEmpty() },
                    tasks = tasks.filter { it.due != null }.groupBy { it.due!! },
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CalendarUiState(month.value, today, today, monthGrid(month.value), emptyMap(), emptyMap()),
        )

    fun previousMonth() = month.update { it.minus(1, DateTimeUnit.MONTH) }

    fun nextMonth() = month.update { it.plus(1, DateTimeUnit.MONTH) }

    fun goToToday() {
        month.value = today.firstOfMonth()
        selected.value = today
    }

    /** Selecting a grey day from the previous/next month also jumps to that month. */
    fun select(date: LocalDate) {
        selected.value = date
        month.value = date.firstOfMonth()
    }

    fun addEvent(title: String, date: LocalDate, start: LocalTime? = null, end: LocalTime? = null) =
        saveEvent(null, title, date, start, end)

    /** Adds a new event, or updates [existing]. */
    fun saveEvent(
        existing: CalendarEvent?,
        title: String,
        date: LocalDate,
        start: LocalTime?,
        end: LocalTime?,
        endDate: LocalDate? = null,
    ) {
        val event = buildEvent(existing, title, date, start, end, endDate) ?: return
        viewModelScope.launch { eventDao.upsert(event) }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch { eventDao.delete(id) }
    }

    /** Puts a deleted event back (for Undo). */
    fun restoreEvent(event: CalendarEvent) {
        viewModelScope.launch { eventDao.upsert(event) }
    }

    fun toggleTask(id: String) {
        viewModelScope.launch { taskDao.toggleDone(id) }
    }
}
