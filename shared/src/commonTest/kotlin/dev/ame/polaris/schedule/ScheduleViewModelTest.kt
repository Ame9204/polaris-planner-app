package dev.ame.polaris.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    private val dao = FakeScheduleDao()
    private val monday = LocalDate(2026, 11, 9) // a Monday

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.watchedViewModel(): ScheduleViewModel {
        val vm = ScheduleViewModel(dao, monday)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun ScheduleViewModel.titles() = state.value.blocks.map { it.title }

    @Test
    fun weekdayBlockShowsMondayButNotSaturday() = runTest {
        val vm = watchedViewModel()
        vm.save(null, "Work", LocalTime(9, 0), LocalTime(17, 0), Weekdays)
        assertEquals(listOf("Work"), vm.titles())

        repeat(5) { vm.next() } // Saturday
        assertTrue(vm.titles().isEmpty())
    }

    @Test
    fun oneOffBlockOnlyShowsOnItsDay() = runTest {
        val vm = watchedViewModel()
        vm.save(null, "Interview", LocalTime(14, 0), LocalTime(15, 0), emptySet())
        assertEquals(monday, dao.items.value.single().date)

        vm.next()
        assertTrue(vm.titles().isEmpty())
        vm.goToToday()
        assertEquals(listOf("Interview"), vm.titles())
    }

    @Test
    fun editingKeepsTheSameBlock() = runTest {
        val vm = watchedViewModel()
        vm.save(null, "Gym", LocalTime(7, 0), LocalTime(8, 0), AllDays)
        val original = dao.items.value.single()

        vm.save(original, "Gym", LocalTime(18, 0), LocalTime(19, 0), setOf(DayOfWeek.MONDAY))

        val edited = dao.items.value.single()
        assertEquals(original.id, edited.id)
        assertEquals(LocalTime(18, 0), edited.start)
        assertNull(edited.date)
    }

    // ---- Week view ----

    private fun ScheduleViewModel.week() = state.value.days.associate { (day, blocks) -> day.dayOfWeek to blocks.map { it.title } }

    @Test
    fun weekViewShowsEachDaysBlocks() = runTest {
        val vm = watchedViewModel()
        vm.save(null, "Work", LocalTime(9, 0), LocalTime(17, 0), Weekdays)
        vm.save(null, "Brunch", LocalTime(11, 0), LocalTime(12, 0), setOf(DayOfWeek.SUNDAY))
        vm.save(null, "Interview", LocalTime(14, 0), LocalTime(15, 0), emptySet(), onDate = LocalDate(2026, 11, 11))

        vm.setView(ScheduleView.Week)

        val week = vm.week()
        assertEquals(7, week.size)
        assertEquals(listOf("Work"), week[DayOfWeek.MONDAY])
        assertEquals(listOf("Work", "Interview"), week[DayOfWeek.WEDNESDAY])
        assertEquals(listOf("Brunch"), week[DayOfWeek.SUNDAY])
    }

    @Test
    fun arrowsMoveAWeekInWeekView() = runTest {
        val vm = watchedViewModel()
        vm.setView(ScheduleView.Week)
        vm.next()
        assertEquals(LocalDate(2026, 11, 16), vm.state.value.days.first().first) // next Monday
    }

    @Test
    fun oneOffsInOtherWeeksAreNotShown() = runTest {
        val vm = watchedViewModel()
        vm.save(null, "Next week", LocalTime(9, 0), LocalTime(10, 0), emptySet(), onDate = LocalDate(2026, 11, 17))
        vm.setView(ScheduleView.Week)
        assertTrue(vm.week().values.all { it.isEmpty() })
    }

    @Test
    fun openingADayFromTheWeekSwitchesToDayView() = runTest {
        val vm = watchedViewModel()
        vm.setView(ScheduleView.Week)
        vm.openDay(LocalDate(2026, 11, 13))
        assertEquals(ScheduleView.Day, vm.state.value.view)
        assertEquals(LocalDate(2026, 11, 13), vm.state.value.date)
    }

    @Test
    fun endNotAfterStartIsRejected() = runTest {
        val vm = watchedViewModel()
        vm.save(null, "Backwards", LocalTime(10, 0), LocalTime(9, 0), AllDays)
        assertTrue(dao.items.value.isEmpty())
    }
}
