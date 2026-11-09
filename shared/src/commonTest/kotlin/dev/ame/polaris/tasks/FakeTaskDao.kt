package dev.ame.polaris.tasks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

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

    override suspend fun upsert(task: Task) {
        items.update { list -> list.filterNot { it.id == task.id } + task }
    }

    override suspend fun toggleDone(id: String) {
        items.update { list -> list.map { if (it.id == id) it.copy(done = !it.done) else it } }
    }

    override suspend fun delete(id: String) {
        items.update { list -> list.filterNot { it.id == id } }
    }
}
