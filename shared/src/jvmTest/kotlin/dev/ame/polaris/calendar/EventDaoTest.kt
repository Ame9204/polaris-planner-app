package dev.ame.polaris.calendar

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ame.polaris.data.PolarisDatabase
import dev.ame.polaris.tasks.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs the real Room queries against a throwaway in-memory database. */
class EventDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<PolarisDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    @AfterTest
    fun closeDb() = db.close()

    @Test
    fun betweenIsInclusiveAndAllDayComesFirst() = runTest {
        val dao = db.eventDao()
        dao.upsert(CalendarEvent("a", "Before range", LocalDate(2026, 10, 31), createdAt = 1))
        dao.upsert(CalendarEvent("b", "Meeting", LocalDate(2026, 11, 1), LocalTime(9, 30), LocalTime(10, 0), createdAt = 2))
        dao.upsert(CalendarEvent("c", "Holiday", LocalDate(2026, 11, 1), createdAt = 3))
        dao.upsert(CalendarEvent("d", "Last day", LocalDate(2026, 11, 30), createdAt = 4))

        val found = dao.observeBetween(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)).first()

        assertEquals(listOf("Holiday", "Meeting", "Last day"), found.map { it.title })
        assertEquals(LocalTime(9, 30), found[1].start) // time survived the text round-trip
    }

    @Test
    fun dueBetweenSkipsTasksWithoutADate() = runTest {
        val dao = db.taskDao()
        dao.upsert(Task("1", "Due", due = LocalDate(2026, 11, 12), createdAt = 1))
        dao.upsert(Task("2", "No date", createdAt = 2))
        dao.upsert(Task("3", "Next month", due = LocalDate(2026, 12, 12), createdAt = 3))

        val found = dao.observeDueBetween(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)).first()

        assertEquals(listOf("Due"), found.map { it.title })
    }

    @Test
    fun multiDayEventsShowUpOnEveryDayTheyCover() = runTest {
        val dao = db.eventDao()
        dao.upsert(CalendarEvent("t", "Trip", LocalDate(2026, 11, 7), createdAt = 1, endDate = LocalDate(2026, 11, 10)))

        // Started before the range, still running inside it
        assertEquals(listOf("Trip"), dao.observeBetween(LocalDate(2026, 11, 9), LocalDate(2026, 11, 9)).first().map { it.title })
        // Over before the range
        assertEquals(emptyList(), dao.observeBetween(LocalDate(2026, 11, 11), LocalDate(2026, 11, 20)).first())
        // The end date survives the round trip
        assertEquals(LocalDate(2026, 11, 10), dao.observeBetween(LocalDate(2026, 11, 1), LocalDate(2026, 11, 30)).first().single().endDate)
    }
}
