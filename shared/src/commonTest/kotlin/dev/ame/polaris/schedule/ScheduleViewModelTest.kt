package dev.ame.polaris.schedule

import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.calendar.FakeEventDao
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
    private val events = FakeEventDao()
    private val monday = LocalDate(2026, 11, 9) // a Monday

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.watchedViewModel(): ScheduleViewModel {
        val vm = ScheduleViewModel(dao, events, monday)
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
        assertEquals(LocalDate(2026, 11, 16), vm.state.value.days.first().date) // next Monday
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

    // ---- Events on the timeline ----

    @Test
    fun calendarEventsShowOnTheirDay() = runTest {
        events.upsert(CalendarEvent("e1", "Dentist", monday, LocalTime(14, 0), createdAt = 1))
        events.upsert(CalendarEvent("e2", "Holiday", monday, createdAt = 2))
        events.upsert(CalendarEvent("e3", "Tuesday thing", LocalDate(2026, 11, 10), LocalTime(9, 0), createdAt = 3))
        val vm = watchedViewModel()

        val today = vm.state.value.days.single()
        assertEquals(listOf("Dentist", "Holiday"), today.events.map { it.title })
        assertEquals(listOf("Holiday"), today.allDayEvents.map { it.title })
        // Only the timed event goes on the timeline itself
        assertEquals(listOf("Dentist"), today.entries.filterIsInstance<TimelineEntry.Event>().map { it.event.title })
    }

    @Test
    fun weekViewSortsEventsIntoDays() = runTest {
        events.upsert(CalendarEvent("e", "Tuesday thing", LocalDate(2026, 11, 10), LocalTime(9, 0), createdAt = 1))
        val vm = watchedViewModel()
        vm.setView(ScheduleView.Week)
        val byDay = vm.state.value.days.associate { it.date.dayOfWeek to it.events.map { e -> e.title } }
        assertEquals(listOf("Tuesday thing"), byDay[DayOfWeek.TUESDAY])
        assertTrue(byDay[DayOfWeek.MONDAY]!!.isEmpty())
    }

    @Test
    fun editingAnEventFromTheScheduleKeepsItsId() = runTest {
        val original = CalendarEvent("e", "Dentist", monday, LocalTime(14, 0), createdAt = 1)
        events.upsert(original)
        val vm = watchedViewModel()

        vm.saveEvent(original, "Dentist (moved)", monday, LocalTime(15, 0), null)

        val saved = events.items.value.single()
        assertEquals("e", saved.id)
        assertEquals(LocalTime(15, 0), saved.start)
    }

    @Test
    fun blockReminderSettingIsSaved() = runTest {
        val vm = watchedViewModel()
        vm.save(null, "Gym", LocalTime(7, 0), LocalTime(8, 0), AllDays, remindMinutesBefore = 10)
        vm.save(null, "Sleep", LocalTime(23, 0), LocalTime(23, 59), AllDays, remindMinutesBefore = null)
        val byTitle = dao.items.value.associate { it.title to it.remindMinutesBefore }
        assertEquals(10, byTitle["Gym"])
        assertNull(byTitle["Sleep"])
    }

    @Test
    fun endNotAfterStartIsRejected() = runTest {
        val vm = watchedViewModel()
        vm.save(null, "Backwards", LocalTime(10, 0), LocalTime(9, 0), AllDays)
        assertTrue(dao.items.value.isEmpty())
    }
}
