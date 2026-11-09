package dev.ame.polaris.tasks

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * DAO = Data Access Object: every way the app reads or writes tasks.
 * You write the SQL or annotation; Room generates the code at build time.
 */
@Dao
interface TaskDao {

    /** A Flow re-emits the whole list every time the table changes, so the UI stays current. */
    @Query("SELECT * FROM tasks ORDER BY createdAt")
    fun observeAll(): Flow<List<Task>>

    /** Tasks due from [from] to [to], inclusive. Tasks with no due date are never included. */
    @Query("SELECT * FROM tasks WHERE due BETWEEN :from AND :to ORDER BY due, createdAt")
    fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<Task>>

    /** Unfinished tasks due on or before [to], including overdue ones. Used by Overview. */
    @Query("SELECT * FROM tasks WHERE done = 0 AND due IS NOT NULL AND due <= :to ORDER BY due, createdAt")
    fun observeOpenDueUntil(to: LocalDate): Flow<List<Task>>

    /** Insert, or replace if a task with the same id exists. */
    @Upsert
    suspend fun upsert(task: Task)

    @Query("UPDATE tasks SET done = NOT done WHERE id = :id")
    suspend fun toggleDone(id: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)
}
