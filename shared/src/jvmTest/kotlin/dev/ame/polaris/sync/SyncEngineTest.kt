package dev.ame.polaris.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.data.PolarisDatabase
import dev.ame.polaris.notes.Note
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.tasks.Task
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Instant

/**
 * A pretend Supabase that follows the same rules as supabase/schema.sql:
 * a row only replaces the stored one if its changed_at is newer, and every accepted
 * change gets a fresh server timestamp (synced_at).
 */
private class FakeServer : SyncRemote {
    val tables = mutableMapOf<String, MutableMap<String, JsonObject>>()
    private var clock = Instant.parse("2026-11-10T00:00:00Z")

    override suspend fun push(table: String, rows: List<JsonObject>) {
        val stored = tables.getOrPut(table) { mutableMapOf() }
        for (row in rows) {
            val id = row.getValue("id").jsonPrimitive.content
            val old = stored[id]
            if (old == null || row.changedAt() > old.changedAt()) {
                clock += 1.microseconds
                stored[id] = JsonObject(row + ("synced_at" to JsonPrimitive(clock.toString())))
            }
        }
    }

    override suspend fun pull(table: String, since: String?, limit: Int): List<JsonObject> =
        tables[table].orEmpty().values
            .filter { since == null || Instant.parse(it.syncedAt()) >= Instant.parse(since) }
            .sortedBy { Instant.parse(it.syncedAt()) }
            .take(limit)

    private fun JsonObject.changedAt() = getValue("changed_at").jsonPrimitive.long
    private fun JsonObject.syncedAt() = getValue("synced_at").jsonPrimitive.content
}

class SyncEngineTest {

    private fun newDevice() = Room.inMemoryDatabaseBuilder<PolarisDatabase>().setDriver(BundledSQLiteDriver()).build()

    private val server = FakeServer()
    private val phone = newDevice()
    private val desktop = newDevice()

    private fun engine(db: PolarisDatabase, pageSize: Int = 500) =
        SyncEngine(syncTables(db), server, db.settingDao(), pageSize)

    private suspend fun sync(db: PolarisDatabase) = engine(db).syncOnce()

    @AfterTest
    fun close() {
        phone.close()
        desktop.close()
    }

    @Test
    fun aTaskAddedOnThePhoneAppearsOnTheDesktop() = runTest {
        phone.taskDao().upsert(Task("t", "Pay rent", due = LocalDate(2026, 11, 12), createdAt = 1, remindAt = LocalDateTime(2026, 11, 12, 9, 30)))

        sync(phone)
        sync(desktop)

        val onDesktop = desktop.taskDao().observeAll().first().single()
        assertEquals("Pay rent", onDesktop.title)
        assertEquals(LocalDateTime(2026, 11, 12, 9, 30), onDesktop.remindAt) // every field survives the trip
    }

    @Test
    fun editsTravelBothWays() = runTest {
        phone.taskDao().upsert(Task("t", "Pay rent", createdAt = 1))
        sync(phone); sync(desktop)

        desktop.taskDao().toggleDone("t")
        sync(desktop); sync(phone)

        assertEquals(true, phone.taskDao().observeAll().first().single().done)
    }

    @Test
    fun deletingOnOneDeviceDeletesOnTheOther() = runTest {
        phone.noteDao().upsert(Note("n", "Ideas", createdAt = 1, updatedAt = 1))
        sync(phone); sync(desktop)
        assertEquals(1, desktop.noteDao().observeSearch("").first().size)

        phone.noteDao().delete("n")
        sync(phone); sync(desktop)

        assertTrue(desktop.noteDao().observeSearch("").first().isEmpty())
    }

    @Test
    fun newestEditWinsEvenIfItArrivesFirst() = runTest {
        val event = CalendarEvent("e", "Dentist", LocalDate(2026, 11, 12), LocalTime(14, 0), createdAt = 1, changedAt = 100)
        phone.eventDao().upsertRaw(event)
        sync(phone); sync(desktop)

        // Both edit while offline: the desktop's edit is newer (300), the phone's older (200)…
        desktop.eventDao().upsertRaw(event.copy(title = "Dentist 15:00", changedAt = 300))
        phone.eventDao().upsertRaw(event.copy(title = "Dentist (old edit)", changedAt = 200))
        // …and the desktop happens to sync first, the phone (older edit) later
        sync(desktop); sync(phone); sync(desktop)

        assertEquals("Dentist 15:00", phone.eventDao().findById("e")!!.title)
        assertEquals("Dentist 15:00", desktop.eventDao().findById("e")!!.title)
    }

    @Test
    fun aLateUploadIsNotMissed() = runTest {
        // The desktop has already synced recently…
        sync(desktop)
        // …then the phone uploads an edit it made earlier while offline
        phone.scheduleDao().upsertRaw(
            ScheduleBlock("b", "Gym", LocalTime(7, 0), LocalTime(8, 0), days = 127, createdAt = 1, changedAt = 5)
        )
        sync(phone)

        sync(desktop)
        assertEquals("Gym", desktop.scheduleDao().findById("b")?.title)
    }

    @Test
    fun theFirstSyncUploadsDataFromBeforeSyncExisted() = runTest {
        // Rows that existed before the upgrade have changedAt = 0
        phone.taskDao().upsertRaw(Task("old", "From before sync", createdAt = 1, changedAt = 0))

        sync(phone); sync(desktop)

        assertEquals("From before sync", desktop.taskDao().findById("old")?.title)
    }

    @Test
    fun manyRowsArriveAcrossSeveralPages() = runTest {
        repeat(7) { i -> phone.taskDao().upsert(Task("t$i", "Task $i", createdAt = i.toLong())) }
        sync(phone)

        engine(desktop, pageSize = 3).syncOnce()

        assertEquals(7, desktop.taskDao().observeAll().first().size)
    }

    @Test
    fun syncingAgainWithNothingNewChangesNothing() = runTest {
        phone.taskDao().upsert(Task("t", "x", createdAt = 1))
        sync(phone); sync(desktop)

        val again = sync(desktop)

        assertEquals(0, again.pulled) // nothing re-stored
    }

    @Test
    fun resetStartsFromScratch() = runTest {
        phone.taskDao().upsert(Task("t", "x", createdAt = 1))
        sync(phone)
        engine(phone).reset()
        server.tables.clear() // e.g. signing in to a brand-new account

        sync(phone)

        assertEquals(1, server.tables["tasks"]?.size) // everything uploaded again
    }
}
