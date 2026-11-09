package dev.ame.polaris.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.calendar.EventDao
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.schedule.ScheduleDao
import dev.ame.polaris.schedule.bit
import dev.ame.polaris.tasks.Task
import dev.ame.polaris.tasks.TaskDao
import dev.ame.polaris.util.today
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Today's slice of every other page. */
data class OverviewUiState(
    val today: LocalDate,
    val blocks: List<ScheduleBlock> = emptyList(),   // today's schedule, by start time
    val events: List<CalendarEvent> = emptyList(),   // the next few events, however far ahead
    val tasks: List<Task> = emptyList(),             // the most urgent unfinished tasks
    val todayEvents: List<CalendarEvent> = emptyList(), // everything on today (for the count)
    val dueTasks: List<Task> = emptyList(),          // all tasks due today or overdue (for the count)
)

/**
 * Overview has no table of its own: it only reads the other pages' data.
 * combine() watches all its queries and rebuilds the state when any of them changes,
 * so ticking a task on the Tasks page updates this page too.
 */
class OverviewViewModel(
    eventDao: EventDao,
    private val taskDao: TaskDao,
    scheduleDao: ScheduleDao,
    today: LocalDate = today(),
) : ViewModel() {

    val state: StateFlow<OverviewUiState> = combine(
        scheduleDao.observeForDay(today, today.dayOfWeek.bit()),
        // A few extra, because today's already-finished events are hidden from the card
        eventDao.observeUpcoming(today, GlanceCount + 5),
        taskDao.observeNextDue(GlanceCount),
        eventDao.observeBetween(today, today),
        taskDao.observeOpenDueUntil(today),
    ) { blocks, events, tasks, todayEvents, dueTasks ->
        OverviewUiState(today, blocks, events, tasks, todayEvents, dueTasks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState(today))

    fun completeTask(id: String) {
        viewModelScope.launch { taskDao.toggleDone(id) }
    }
}
