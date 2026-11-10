package dev.ame.polaris.sync

import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.data.PolarisDatabase
import dev.ame.polaris.notes.Note
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.tasks.Task
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// How each local table looks in the cloud (supabase/schema.sql), and how to convert.
// Cloud column names use snake_case; user_id and synced_at are filled in by the server.

@Serializable
data class TaskRow(
    val id: String,
    val title: String,
    val done: Boolean,
    val due: LocalDate?,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("remind_at") val remindAt: LocalDateTime?,
    @SerialName("changed_at") val changedAt: Long,
    val deleted: Boolean,
)

@Serializable
data class EventRow(
    val id: String,
    val title: String,
    val date: LocalDate,
    @SerialName("end_date") val endDate: LocalDate?,
    @SerialName("start_time") val start: LocalTime?,
    @SerialName("end_time") val end: LocalTime?,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("changed_at") val changedAt: Long,
    val deleted: Boolean,
)

@Serializable
data class BlockRow(
    val id: String,
    val title: String,
    @SerialName("start_time") val start: LocalTime,
    @SerialName("end_time") val end: LocalTime,
    val days: Int,
    val date: LocalDate?,
    @SerialName("remind_minutes_before") val remindMinutesBefore: Int?,
    @SerialName("starts_on") val startsOn: LocalDate? = null,
    @SerialName("ends_on") val endsOn: LocalDate? = null,
    @SerialName("skip_dates") val skipDates: String = "",
    @SerialName("created_at") val createdAt: Long,
    @SerialName("changed_at") val changedAt: Long,
    val deleted: Boolean,
)

@Serializable
data class NoteRow(
    val id: String,
    val title: String,
    val body: String,
    val pinned: Boolean,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    @SerialName("changed_at") val changedAt: Long,
    val deleted: Boolean,
)

/**
 * JSON for the cloud. encodeDefaults + explicit nulls: every column is always sent,
 * so clearing a value (e.g. removing a reminder) really clears it in the cloud too.
 */
val SyncJson = Json {
    ignoreUnknownKeys = true // the server adds user_id and synced_at
    encodeDefaults = true
    explicitNulls = true
}

/**
 * Everything the sync engine needs to know about one table: how to read local changes,
 * look up and store rows, and convert to and from the cloud's shape.
 * [L] is the app's type (Task…), [R] the cloud's (TaskRow…).
 */
class SyncTable<L, R>(
    /** Table name in the cloud (and the key for this table's sync progress). */
    val name: String,
    private val serializer: KSerializer<R>,
    private val changedSince: suspend (Long) -> List<L>,
    private val findById: suspend (String) -> L?,
    private val storeAsIs: suspend (L) -> Unit,
    private val toRow: (L) -> R,
    private val fromRow: (R) -> L,
    private val changedAtOf: (L) -> Long,
) {
    /** Local rows changed after [since], as cloud JSON, plus the newest changedAt among them. */
    suspend fun localChanges(since: Long): Pair<List<JsonObject>, Long?> {
        val rows = changedSince(since)
        val json = rows.map { SyncJson.encodeToJsonElement(serializer, toRow(it)).jsonObject }
        return json to rows.maxOfOrNull(changedAtOf)
    }

    /**
     * Stores rows that came from the cloud, keeping whichever version is newest:
     * a cloud row only replaces the local one if it changed at the same time or later.
     * Returns how many rows were actually stored.
     */
    suspend fun applyFromCloud(rows: List<JsonObject>, idOf: (JsonObject) -> String): Int {
        var stored = 0
        for (obj in rows) {
            val incoming = fromRow(SyncJson.decodeFromJsonElement(serializer, obj))
            val existing = findById(idOf(obj))
            if (existing == null || changedAtOf(incoming) > changedAtOf(existing)) {
                storeAsIs(incoming)
                stored++
            }
        }
        return stored
    }
}

/** The four tables that sync, wired to the app's database. (Settings stay on each device.) */
fun syncTables(db: PolarisDatabase): List<SyncTable<*, *>> {
    val tasks = db.taskDao()
    val events = db.eventDao()
    val blocks = db.scheduleDao()
    val notes = db.noteDao()
    return listOf(
        SyncTable(
            "tasks", TaskRow.serializer(), tasks::changedSince, tasks::findById, tasks::upsertRaw,
            toRow = { TaskRow(it.id, it.title, it.done, it.due, it.createdAt, it.remindAt, it.changedAt, it.deleted) },
            fromRow = { Task(it.id, it.title, it.done, it.due, it.createdAt, it.remindAt, it.changedAt, it.deleted) },
            changedAtOf = { it.changedAt },
        ),
        SyncTable(
            "events", EventRow.serializer(), events::changedSince, events::findById, events::upsertRaw,
            toRow = { EventRow(it.id, it.title, it.date, it.endDate, it.start, it.end, it.createdAt, it.changedAt, it.deleted) },
            fromRow = {
                CalendarEvent(
                    id = it.id, title = it.title, date = it.date, start = it.start, end = it.end,
                    createdAt = it.createdAt, endDate = it.endDate, changedAt = it.changedAt, deleted = it.deleted,
                )
            },
            changedAtOf = { it.changedAt },
        ),
        SyncTable(
            "schedule_blocks", BlockRow.serializer(), blocks::changedSince, blocks::findById, blocks::upsertRaw,
            toRow = {
                BlockRow(
                    it.id, it.title, it.start, it.end, it.days, it.date, it.remindMinutesBefore,
                    it.startsOn, it.endsOn, it.skipDates, it.createdAt, it.changedAt, it.deleted,
                )
            },
            fromRow = {
                ScheduleBlock(
                    id = it.id, title = it.title, start = it.start, end = it.end, days = it.days, date = it.date,
                    createdAt = it.createdAt, remindMinutesBefore = it.remindMinutesBefore,
                    startsOn = it.startsOn, endsOn = it.endsOn, skipDates = it.skipDates,
                    changedAt = it.changedAt, deleted = it.deleted,
                )
            },
            changedAtOf = { it.changedAt },
        ),
        SyncTable(
            "notes", NoteRow.serializer(), notes::changedSince, notes::findById, notes::upsertRaw,
            toRow = { NoteRow(it.id, it.title, it.body, it.pinned, it.createdAt, it.updatedAt, it.changedAt, it.deleted) },
            fromRow = { Note(it.id, it.title, it.body, it.pinned, it.createdAt, it.updatedAt, it.changedAt, it.deleted) },
            changedAtOf = { it.changedAt },
        ),
    )
}
