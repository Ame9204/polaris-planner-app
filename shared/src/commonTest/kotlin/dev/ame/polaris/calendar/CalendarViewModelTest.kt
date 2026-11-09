package dev.ame.polaris.calendar

import dev.ame.polaris.tasks.FakeTaskDao
import dev.ame.polaris.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private val events = FakeEventDao()
    private val tasks = FakeTaskDao()
    private val today = LocalDate(2026, 11, 9)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A ViewModel whose `state` is being watched, like it is on screen. */
    private fun TestScope.watchedViewModel(): CalendarViewModel {
        val vm = CalendarViewModel(events, tasks, today)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun startsOnTodaysMonth() = runTest {
        val vm = watchedViewModel()
        assertEquals(LocalDate(2026, 11, 1), vm.state.value.month)
        assertEquals(today, vm.state.value.selected)
    }

    @Test
    fun addedEventShowsOnItsDay() = runTest {
        val vm = watchedViewModel()
        vm.addEvent("Dentist", LocalDate(2026, 11, 12), LocalTime(9, 30), LocalTime(10, 0))

        vm.select(LocalDate(2026, 11, 12))

        assertEquals(listOf("Dentist"), vm.state.value.selectedEvents.map { it.title })
    }

    @Test
    fun tasksWithDueDateShowAndTasksWithoutDontShow() = runTest {
        tasks.upsert(Task(id = "1", title = "Due task", due = today, createdAt = 1))
        tasks.upsert(Task(id = "2", title = "Someday", createdAt = 2))

        val vm = watchedViewModel()

        assertEquals(listOf("Due task"), vm.state.value.selectedTasks.map { it.title })
    }

    @Test
    fun selectingADayInAnotherMonthJumpsToIt() = runTest {
        val vm = watchedViewModel()
        vm.select(LocalDate(2026, 12, 3))
        assertEquals(LocalDate(2026, 12, 1), vm.state.value.month)
    }

    @Test
    fun nextAndPreviousMonth() = runTest {
        val vm = watchedViewModel()
        vm.nextMonth()
        assertEquals(LocalDate(2026, 12, 1), vm.state.value.month)
        vm.previousMonth()
        vm.previousMonth()
        assertEquals(LocalDate(2026, 10, 1), vm.state.value.month)
    }

    @Test
    fun endBeforeStartIsDropped() = runTest {
        val vm = watchedViewModel()
        vm.addEvent("Oops", today, LocalTime(14, 0), LocalTime(13, 0))
        assertNull(events.items.value.single().end)
    }
}
