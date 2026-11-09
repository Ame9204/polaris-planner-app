package dev.ame.polaris.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Holds the Tasks page's state and the actions that change it.
 * The screen only reads [tasks] and calls these functions; it never edits the list itself.
 *
 * The database is the single source of truth: actions write to it, and [tasks]
 * updates by itself because it's built from the DAO's Flow.
 */
class TasksViewModel(private val dao: TaskDao) : ViewModel() {

    /**
     * stateIn turns the database Flow into a StateFlow the UI can read.
     * WhileSubscribed(5_000) stops watching the database 5 s after the screen goes away,
     * but survives a quick screen rotation without re-querying.
     */
    val tasks: StateFlow<List<Task>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalUuidApi::class)
    fun add(title: String, due: LocalDate? = null) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        val task = Task(
            id = Uuid.random().toString(),
            title = clean,
            due = due,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )
        // DAO functions are `suspend` (disk work), so run them in a coroutine
        // tied to this ViewModel's lifetime.
        viewModelScope.launch { dao.upsert(task) }
    }

    fun toggleDone(id: String) {
        viewModelScope.launch { dao.toggleDone(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { dao.delete(id) }
    }

    /** Sets the task's own reminder, or removes it with null. */
    fun setReminder(id: String, at: LocalDateTime?) {
        viewModelScope.launch { dao.setReminder(id, at) }
    }

    /** Puts a deleted task back exactly as it was (for Undo). */
    fun restore(task: Task) {
        viewModelScope.launch { dao.upsert(task) }
    }
}
