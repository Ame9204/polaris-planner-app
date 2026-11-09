package dev.ame.polaris.notes

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ame.polaris.data.PolarisDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks the real SQL for search, ordering and partial updates. */
class NoteDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<PolarisDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val dao = db.noteDao()

    @AfterTest
    fun closeDb() = db.close()

    @Test
    fun searchMatchesTitleOrBodyIgnoringCase() = runTest {
        dao.upsert(Note("1", title = "Groceries", body = "Milk", createdAt = 1, updatedAt = 1))
        dao.upsert(Note("2", title = "Ideas", body = "nothing", createdAt = 2, updatedAt = 2))

        assertEquals(listOf("1"), dao.observeSearch("milk").first().map { it.id })
        assertEquals(listOf("2", "1"), dao.observeSearch("").first().map { it.id }) // newest first
    }

    @Test
    fun pinnedFirstAndUpdateKeepsPin() = runTest {
        dao.upsert(Note("a", title = "A", createdAt = 1, updatedAt = 1))
        dao.upsert(Note("b", title = "B", createdAt = 2, updatedAt = 2))
        dao.togglePinned("a")

        dao.updateContent("a", "A edited", "text", updatedAt = 3)

        val notes = dao.observeSearch("").first()
        assertEquals(listOf("a", "b"), notes.map { it.id })
        assertEquals(true, notes.first().pinned) // the text update didn't undo the pin
        assertEquals("A edited", notes.first().title)
    }
}
