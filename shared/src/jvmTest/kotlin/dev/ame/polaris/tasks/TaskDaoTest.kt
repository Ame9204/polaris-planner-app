package dev.ame.polaris.tasks

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ame.polaris.data.PolarisDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Runs the real Room code against a throwaway in-memory database. */
class TaskDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<PolarisDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val dao = db.taskDao()

    @AfterTest
    fun closeDb() = db.close()

    @Test
    fun savesAndReadsBackTasksInOrder() = runTest {
        dao.upsert(Task(id = "b", title = "Second", createdAt = 2, due = LocalDate(2026, 11, 12)))
        dao.upsert(Task(id = "a", title = "First", createdAt = 1))

        val tasks = dao.observeAll().first()

        assertEquals(listOf("First", "Second"), tasks.map { it.title })
        assertEquals(LocalDate(2026, 11, 12), tasks[1].due) // date survived the text round-trip
    }

    @Test
    fun openDueUntilSkipsDoneUndatedAndLaterTasks() = runTest {
        dao.upsert(Task(id = "1", title = "Overdue", due = LocalDate(2026, 11, 1), createdAt = 1))
        dao.upsert(Task(id = "2", title = "Done", due = LocalDate(2026, 11, 9), done = true, createdAt = 2))
        dao.upsert(Task(id = "3", title = "No date", createdAt = 3))
        dao.upsert(Task(id = "4", title = "Too late", due = LocalDate(2026, 11, 30), createdAt = 4))
        dao.upsert(Task(id = "5", title = "Last day", due = LocalDate(2026, 11, 15), createdAt = 5))

        val found = dao.observeOpenDueUntil(LocalDate(2026, 11, 15)).first()

        assertEquals(listOf("Overdue", "Last day"), found.map { it.title })
    }

    @Test
    fun toggleAndDelete() = runTest {
        dao.upsert(Task(id = "a", title = "A", createdAt = 1))

        dao.toggleDone("a")
        assertEquals(true, dao.observeAll().first().single().done)

        dao.delete("a")
        assertEquals(emptyList(), dao.observeAll().first())
    }
}
