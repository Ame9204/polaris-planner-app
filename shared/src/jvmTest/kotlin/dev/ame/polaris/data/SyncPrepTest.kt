package dev.ame.polaris.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.notes.Note
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.tasks.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The groundwork for cloud sync, against the real database:
 * every write stamps `changedAt`, and deleting leaves a hidden marker instead of erasing.
 */
class SyncPrepTest {

    private val db = Room.inMemoryDatabaseBuilder<PolarisDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    @AfterTest
    fun closeDb() = db.close()

    @Test
    fun deletingATaskLeavesAHiddenMarker() = runTest {
        val dao = db.taskDao()
        dao.upsert(Task("t", "Pay rent", createdAt = 1))

        dao.delete("t")

        assertTrue(dao.observeAll().first().isEmpty())                // gone from the app
        val marker = dao.changedSince(0).single()                      // but still there for sync
        assertEquals(true, marker.deleted)
        assertTrue(marker.changedAt > 0)
    }

    @Test
    fun undoBringsItBackAsNotDeleted() = runTest {
        val dao = db.taskDao()
        val task = Task("t", "Pay rent", createdAt = 1)
        dao.upsert(task)
        dao.delete("t")

        dao.upsert(task) // what Undo does

        assertEquals(listOf("Pay rent"), dao.observeAll().first().map { it.title })
        assertEquals(false, dao.changedSince(0).single().deleted)
    }

    @Test
    fun everyKindOfWriteStampsChangedAt() = runTest {
        val tasks = db.taskDao()
        tasks.upsertRaw(Task("t", "x", createdAt = 1, changedAt = 5))
        assertTrue(tasks.changedSince(5).isEmpty()) // nothing new yet

        tasks.toggleDone("t")
        assertTrue(tasks.changedSince(5).single().changedAt > 5)

        val stamp = tasks.changedSince(0).single().changedAt
        tasks.setReminder("t", null)
        assertTrue(tasks.changedSince(0).single().changedAt >= stamp)
    }

    @Test
    fun syncCanStoreCloudRowsExactlyAsTheyAre() = runTest {
        val dao = db.eventDao()
        // upsertRaw keeps the cloud's own changedAt (upsert would stamp "now")
        dao.upsertRaw(CalendarEvent("e", "From the cloud", LocalDate(2026, 11, 12), createdAt = 1, changedAt = 1234))
        assertEquals(1234, dao.changedSince(0).single().changedAt)
    }

    @Test
    fun deletedRowsAreHiddenEverywhere() = runTest {
        val day = LocalDate(2026, 11, 9)
        db.eventDao().upsert(CalendarEvent("e", "Gone", day, LocalTime(9, 0), createdAt = 1))
        db.eventDao().delete("e")
        db.scheduleDao().upsert(ScheduleBlock("b", "Gone", LocalTime(9, 0), LocalTime(10, 0), days = 127, createdAt = 1))
        db.scheduleDao().delete("b")
        db.taskDao().upsert(Task("t", "Gone", due = day, createdAt = 1))
        db.taskDao().delete("t")

        assertTrue(db.eventDao().observeBetween(day, day).first().isEmpty())
        assertTrue(db.eventDao().observeUpcoming(day, 10).first().isEmpty())
        assertTrue(db.scheduleDao().observeForDay(day, 1).first().isEmpty())
        assertTrue(db.scheduleDao().observeForRange(day, day).first().isEmpty())
        assertTrue(db.taskDao().observeDueBetween(day, day).first().isEmpty())
        assertTrue(db.taskDao().observeNextDue(10).first().isEmpty())
    }

    @Test
    fun pinningANoteSyncsButDoesNotCountAsEditing() = runTest {
        val dao = db.noteDao()
        dao.upsertRaw(Note("n", "Ideas", createdAt = 1, updatedAt = 100, changedAt = 100))

        dao.togglePinned("n")

        val note = dao.observeSearch("").first().single()
        assertEquals(100, note.updatedAt)     // the "edited" time you see didn't move
        assertTrue(note.changedAt > 100)      // but sync will pick it up
    }
}
