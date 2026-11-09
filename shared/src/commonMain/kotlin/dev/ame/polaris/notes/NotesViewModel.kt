package dev.ame.polaris.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ame.polaris.util.newId
import dev.ame.polaris.util.nowMillis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NotesUiState(
    val query: String = "",
    val notes: List<Note> = emptyList(),
    val selectedId: String? = null,
) {
    val selected: Note? get() = notes.firstOrNull { it.id == selectedId }
}

/** How long typing has to pause before it's written to the database. */
const val SaveDelayMs = 300L

class NotesViewModel(private val dao: NoteDao) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<NotesUiState> = combine(
        query.flatMapLatest { dao.observeSearch(it.trim()) },
        query,
        selectedId,
    ) { notes, q, selected ->
        NotesUiState(q, notes, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    // ---- Autosave ----
    // Each keystroke restarts a short timer; only when typing pauses is the note saved.
    // The Mutex makes saves run one at a time, in order, so an older save can never
    // land after a newer one.
    private val pendingSaves = mutableMapOf<String, Job>()
    private val latestText = mutableMapOf<String, Pair<String, String>>()
    private val writeLock = Mutex()

    fun edit(id: String, title: String, body: String) {
        latestText[id] = title to body
        pendingSaves[id]?.cancel()
        pendingSaves[id] = viewModelScope.launch {
            delay(SaveDelayMs)
            writeLock.withLock { dao.updateContent(id, title, body, nowMillis()) }
            pendingSaves.remove(id)
        }
    }

    // ---- List actions ----

    fun setQuery(text: String) {
        query.value = text
    }

    fun create() {
        leaveCurrent()
        val now = nowMillis()
        val note = Note(id = newId(), createdAt = now, updatedAt = now)
        query.value = "" // so the new note isn't hidden by an old search
        selectedId.value = note.id
        viewModelScope.launch { writeLock.withLock { dao.upsert(note) } }
    }

    fun select(id: String) {
        if (id == selectedId.value) return
        leaveCurrent()
        selectedId.value = id
    }

    fun close() {
        leaveCurrent()
        selectedId.value = null
    }

    fun togglePinned(id: String) {
        viewModelScope.launch { writeLock.withLock { dao.togglePinned(id) } }
    }

    fun delete(id: String) {
        pendingSaves.remove(id)?.cancel()
        latestText.remove(id)
        if (selectedId.value == id) selectedId.value = null
        viewModelScope.launch { writeLock.withLock { dao.delete(id) } }
    }

    /** Leaving a note you never typed in deletes it, so "New note" then back leaves no empty notes. */
    private fun leaveCurrent() {
        val id = selectedId.value ?: return
        val (title, body) = latestText[id]
            ?: state.value.notes.firstOrNull { it.id == id }?.let { it.title to it.body }
            ?: return
        if (title.isBlank() && body.isBlank()) delete(id)
    }
}
