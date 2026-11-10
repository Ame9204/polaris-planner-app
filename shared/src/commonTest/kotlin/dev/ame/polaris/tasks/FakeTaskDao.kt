package dev.ame.polaris.tasks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** An in-memory stand-in for the real database, so ViewModel tests run instantly. */
class FakeTaskDao : TaskDao {
    val items = MutableStateFlow<List<Task>>(emptyList())

    override fun observeAll(): Flow<List<Task>> = items

    override fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        items.map { list -> list.filter { it.due != null && it.due in from..to } }

    override fun observeOpenDueUntil(to: LocalDate): Flow<List<Task>> =
        items.map { list ->
            list.filter { !it.done && it.due != null && it.due <= to }
                .sortedWith(compareBy({ it.due }, { it.createdAt }))
        }

    override fun observeNextDue(limit: Int): Flow<List<Task>> =
        items.map { list ->
            list.filter { !it.done && it.due != null }
                .sortedWith(compareBy({ it.due }, { it.createdAt }))
                .take(limit)
        }

    override suspend fun upsertRaw(task: Task) {
        items.update { list -> list.filterNot { it.id == task.id } + task }
    }

    override suspend fun toggleDoneAt(id: String, now: Long) {
        items.update { list -> list.map { if (it.id == id) it.copy(done = !it.done) else it } }
    }

    override suspend fun setReminderAt(id: String, at: LocalDateTime?, now: Long) {
        items.update { list -> list.map { if (it.id == id) it.copy(remindAt = at) else it } }
    }

    // The fake simply removes deleted rows; the real deletion markers are tested in jvmTest.
    override suspend fun markDeleted(id: String, now: Long) {
        items.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun changedSince(since: Long): List<Task> = items.value.filter { it.changedAt > since }

    override suspend fun findById(id: String): Task? = items.value.firstOrNull { it.id == id }
}
