package dev.ame.polaris.schedule

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ame.polaris.data.PolarisDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks the real SQL, including the bitwise repeat-day match. */
class ScheduleDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<PolarisDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val dao = db.scheduleDao()

    @AfterTest
    fun closeDb() = db.close()

    @Test
    fun findsRepeatingAndOneOffBlocksForADay() = runTest {
        val monday = LocalDate(2026, 11, 9)
        dao.upsert(ScheduleBlock("1", "Work", LocalTime(9, 0), LocalTime(17, 0), days = Weekdays.toBits(), createdAt = 1))
        dao.upsert(ScheduleBlock("2", "Brunch", LocalTime(11, 0), LocalTime(12, 0), days = Weekend.toBits(), createdAt = 2))
        dao.upsert(ScheduleBlock("3", "Dentist", LocalTime(8, 0), LocalTime(8, 30), date = monday, createdAt = 3))
        dao.upsert(ScheduleBlock("4", "Other day", LocalTime(8, 0), LocalTime(9, 0), date = LocalDate(2026, 11, 10), createdAt = 4))

        val found = dao.observeForDay(monday, DayOfWeek.MONDAY.bit()).first()

        assertEquals(listOf("Dentist", "Work"), found.map { it.title }) // sorted by start time
    }
}
