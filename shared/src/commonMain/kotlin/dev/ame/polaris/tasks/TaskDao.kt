package dev.ame.polaris.tasks

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.ame.polaris.util.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * DAO = Data Access Object: every way the app reads or writes tasks.
 * You write the SQL or annotation; Room generates the code at build time.
 */
@Dao
interface TaskDao {

    /** A Flow re-emits the whole list every time the table changes, so the UI stays current. */
    @Query("SELECT * FROM tasks WHERE deleted = 0 ORDER BY createdAt")
    fun observeAll(): Flow<List<Task>>

    /** Tasks due from [from] to [to], inclusive. Tasks with no due date are never included. */
    @Query("SELECT * FROM tasks WHERE deleted = 0 AND due BETWEEN :from AND :to ORDER BY due, createdAt")
    fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<Task>>

    /** Unfinished tasks due on or before [to], including overdue ones. Used by Overview. */
    @Query("SELECT * FROM tasks WHERE deleted = 0 AND done = 0 AND due IS NOT NULL AND due <= :to ORDER BY due, createdAt")
    fun observeOpenDueUntil(to: LocalDate): Flow<List<Task>>

    /** The [limit] most urgent unfinished tasks with a due date: overdue first, then soonest. */
    @Query("SELECT * FROM tasks WHERE deleted = 0 AND done = 0 AND due IS NOT NULL ORDER BY due, createdAt LIMIT :limit")
    fun observeNextDue(limit: Int): Flow<List<Task>>

    /** Insert, or replace if a task with the same id exists. */

    // Writes go through the plain-named functions (upsert, delete, toggle…), which stamp
    // `changedAt` with the current time so sync knows what changed. The "…At"/"Raw" ones are
    // what Room actually runs; the sync engine uses upsertRaw to store rows from the cloud
    // exactly as they are.

    /** Insert, or replace if a task with the same id exists. */
    suspend fun upsert(task: Task) = upsertRaw(task.copy(changedAt = nowMillis(), deleted = false))

    @Upsert
    suspend fun upsertRaw(task: Task)

    suspend fun toggleDone(id: String) = toggleDoneAt(id, nowMillis())

    @Query("UPDATE tasks SET done = NOT done, changedAt = :now WHERE id = :id")
    suspend fun toggleDoneAt(id: String, now: Long)

    /** Sets (or with null, removes) a task's own reminder time. */
    suspend fun setReminder(id: String, at: LocalDateTime?) = setReminderAt(id, at, nowMillis())

    @Query("UPDATE tasks SET remindAt = :at, changedAt = :now WHERE id = :id")
    suspend fun setReminderAt(id: String, at: LocalDateTime?, now: Long)

    /** Marks the task deleted (hidden everywhere); the marker syncs the deletion. */
    suspend fun delete(id: String) = markDeleted(id, nowMillis())

    @Query("UPDATE tasks SET deleted = 1, changedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    /** Sync: every row changed after [since], deleted ones included. */
    @Query("SELECT * FROM tasks WHERE changedAt > :since")
    suspend fun changedSince(since: Long): List<Task>

    /** Sync: one row by id, deleted or not (to compare with what the cloud sent). */
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun findById(id: String): Task?
}
