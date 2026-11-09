package dev.ame.polaris.calendar

import dev.ame.polaris.overview.eventCountdown
import dev.ame.polaris.schedule.FakeScheduleDao
import dev.ame.polaris.schedule.ScheduleDay
import dev.ame.polaris.schedule.ScheduleViewModel
import dev.ame.polaris.schedule.ScheduleView
import dev.ame.polaris.schedule.TimelineEntry
import dev.ame.polaris.tasks.FakeTaskDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MultiDayEventTest {

    private val mon = LocalDate(2026, 11, 9)
    private val wed = LocalDate(2026, 11, 11)
    private val trip = CalendarEvent("t", "Trip", mon, endDate = wed, createdAt = 1)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun anEventCoversEveryDayFromStartToEnd() {
        assertTrue(trip.isMultiDay)
        assertTrue(trip.occursOn(LocalDate(2026, 11, 10)))
        assertEquals(false, trip.occursOn(LocalDate(2026, 11, 12)))
    }

    @Test
    fun buildEventRules() {
        // Ending on the start day is stored as a one-day event
        assertNull(buildEvent(null, "x", mon, null, null, endDate = mon)!!.endDate)
        // An end date before the start can't happen
        assertNull(buildEvent(null, "x", mon, null, null, endDate = LocalDate(2026, 11, 1))!!.endDate)
        // Multi-day: an end time "earlier" than the start time is fine (it's on a later day)
        val overnight = buildEvent(null, "Flight", mon, LocalTime(22, 0), LocalTime(6, 0), endDate = LocalDate(2026, 11, 10))!!
        assertEquals(LocalTime(6, 0), overnight.end)
        // One day: an end before the start is dropped
        assertNull(buildEvent(null, "x", mon, LocalTime(14, 0), LocalTime(13, 0))!!.end)
    }

    @Test
    fun calendarListsItUnderEachDay() = runTest {
        val events = FakeEventDao()
        events.upsert(trip)
        val vm = CalendarViewModel(events, FakeTaskDao(), today = mon)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        val byDay = vm.state.value.events
        assertEquals(listOf("Trip"), byDay[mon]?.map { it.title })
        assertEquals(listOf("Trip"), byDay[LocalDate(2026, 11, 10)]?.map { it.title })
        assertEquals(listOf("Trip"), byDay[wed]?.map { it.title })
        assertNull(byDay[LocalDate(2026, 11, 12)])
    }

    @Test
    fun scheduleShowsItInTheAllDayStripEachDay() = runTest {
        val events = FakeEventDao()
        // A timed multi-day event: still goes in the strip, not as a tall block
        events.upsert(CalendarEvent("c", "Conference", mon, LocalTime(9, 0), LocalTime(17, 0), createdAt = 1, endDate = wed))
        val vm = ScheduleViewModel(FakeScheduleDao(), events, mon)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        vm.setView(ScheduleView.Week)

        val days: List<ScheduleDay> = vm.state.value.days
        assertEquals(3, days.count { day -> day.allDayEvents.any { it.title == "Conference" } })
        assertTrue(days.all { day -> day.entries.none { it is TimelineEntry.Event } })
    }

    @Test
    fun overviewCountdownForOngoingEvents() {
        val now = LocalTime(12, 0)
        assertEquals("until 11 Nov", eventCountdown(trip, LocalDate(2026, 11, 10), now))
        assertEquals("ends today", eventCountdown(trip, wed, now))
        assertEquals("today", eventCountdown(trip, mon, now)) // first day: counts like an all-day event
    }
}
