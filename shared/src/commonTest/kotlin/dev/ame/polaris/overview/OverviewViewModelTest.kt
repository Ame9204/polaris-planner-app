package dev.ame.polaris.overview

import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.calendar.FakeEventDao
import dev.ame.polaris.schedule.FakeScheduleDao
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.schedule.Weekdays
import dev.ame.polaris.schedule.Weekend
import dev.ame.polaris.schedule.toBits
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {

    private val events = FakeEventDao()
    private val tasks = FakeTaskDao()
    private val schedule = FakeScheduleDao()
    private val monday = LocalDate(2026, 11, 9)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.watchedViewModel(): OverviewViewModel {
        val vm = OverviewViewModel(events, tasks, schedule, monday)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun collectsTodaysSliceOfEveryPage() = runTest {
        schedule.upsert(ScheduleBlock("w", "Work", LocalTime(9, 0), LocalTime(17, 0), days = Weekdays.toBits(), createdAt = 1))
        schedule.upsert(ScheduleBlock("b", "Brunch", LocalTime(11, 0), LocalTime(12, 0), days = Weekend.toBits(), createdAt = 2))
        events.upsert(CalendarEvent("e1", "Dentist", LocalDate(2026, 11, 12), createdAt = 1))
        events.upsert(CalendarEvent("e2", "Too far", LocalDate(2026, 11, 20), createdAt = 2))
        tasks.upsert(Task("t1", "Overdue", due = LocalDate(2026, 11, 1), createdAt = 1))
        tasks.upsert(Task("t2", "Done already", due = monday, done = true, createdAt = 2))
        tasks.upsert(Task("t3", "No date", createdAt = 3))
        tasks.upsert(Task("t4", "Friday", due = LocalDate(2026, 11, 13), createdAt = 4))

        val state = watchedViewModel().state.value

        assertEquals(listOf("Work"), state.blocks.map { it.title })
        assertEquals(listOf("Dentist"), state.events.map { it.title })
        assertEquals(listOf("Overdue", "Friday"), state.tasks.map { it.title })
    }

    @Test
    fun completingATaskRemovesItFromTheList() = runTest {
        tasks.upsert(Task("t1", "Pay rent", due = monday, createdAt = 1))
        val vm = watchedViewModel()

        vm.completeTask("t1")

        assertTrue(vm.state.value.tasks.isEmpty())
        assertTrue(tasks.items.value.single().done) // still exists, just done
    }
}
