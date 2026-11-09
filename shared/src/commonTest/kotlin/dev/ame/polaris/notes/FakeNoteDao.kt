package dev.ame.polaris.notes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** An in-memory stand-in for the notes table, with the same search and order as the SQL. */
class FakeNoteDao : NoteDao {
    val items = MutableStateFlow<List<Note>>(emptyList())

    override fun observeSearch(query: String): Flow<List<Note>> =
        items.map { list ->
            list.filter { it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true) }
                .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
        }

    override suspend fun upsert(note: Note) {
        items.update { list -> list.filterNot { it.id == note.id } + note }
    }

    override suspend fun updateContent(id: String, title: String, body: String, updatedAt: Long) {
        items.update { list ->
            list.map { if (it.id == id) it.copy(title = title, body = body, updatedAt = updatedAt) else it }
        }
    }

    override suspend fun togglePinned(id: String) {
        items.update { list -> list.map { if (it.id == id) it.copy(pinned = !it.pinned) else it } }
    }

    override suspend fun delete(id: String) {
        items.update { list -> list.filterNot { it.id == id } }
    }
}
