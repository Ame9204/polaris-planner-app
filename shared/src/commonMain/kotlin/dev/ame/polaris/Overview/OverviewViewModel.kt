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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** Today's slice of every other page. */
data class OverviewUiState(
    val today: LocalDate,
    val blocks: List<ScheduleBlock> = emptyList(),   // today's schedule, by start time
    val events: List<CalendarEvent> = emptyList(),   // today + the next few days
    val tasks: List<Task> = emptyList(),             // unfinished: overdue + due soon
)

/**
 * Overview has no table of its own: it only reads the other pages' data.
 * combine() watches all three queries and rebuilds the state when any of them changes,
 * so ticking a task on the Tasks page updates this page too.
 */
class OverviewViewModel(
    eventDao: EventDao,
    private val taskDao: TaskDao,
    scheduleDao: ScheduleDao,
    today: LocalDate = today(),
) : ViewModel() {

    private val lastUpcomingDay = today.plus(UpcomingDays - 1, DateTimeUnit.DAY)

    val state: StateFlow<OverviewUiState> = combine(
        scheduleDao.observeForDay(today, today.dayOfWeek.bit()),
        eventDao.observeBetween(today, lastUpcomingDay),
        taskDao.observeOpenDueUntil(lastUpcomingDay),
    ) { blocks, events, tasks ->
        OverviewUiState(today, blocks, events, tasks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState(today))

    fun completeTask(id: String) {
        viewModelScope.launch { taskDao.toggleDone(id) }
    }
}
