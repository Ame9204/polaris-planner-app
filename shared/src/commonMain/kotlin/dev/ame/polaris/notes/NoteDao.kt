package dev.ame.polaris.notes

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.ame.polaris.util.nowMillis
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /**
     * Notes whose title or text contains [query] (case-insensitive for English letters).
     * An empty query matches everything. Pinned notes first, then most recently edited.
     * `||` joins text in SQL, so this becomes LIKE '%query%'.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE deleted = 0 AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun observeSearch(query: String): Flow<List<Note>>


    // Writes go through the plain-named functions (upsert, delete, toggle…), which stamp
    // `changedAt` with the current time so sync knows what changed. The "…At"/"Raw" ones are
    // what Room actually runs; the sync engine uses upsertRaw to store rows from the cloud
    // exactly as they are.

    suspend fun upsert(note: Note) = upsertRaw(note.copy(changedAt = nowMillis(), deleted = false))

    @Upsert
    suspend fun upsertRaw(note: Note)

    /** Saves typing without touching other fields (like `pinned`) that may have changed meanwhile. */
    @Query("UPDATE notes SET title = :title, body = :body, updatedAt = :updatedAt, changedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, title: String, body: String, updatedAt: Long)

    // Pinning changes changedAt (so it syncs) but not updatedAt (the "edited" time you see)
    suspend fun togglePinned(id: String) = togglePinnedAt(id, nowMillis())

    @Query("UPDATE notes SET pinned = NOT pinned, changedAt = :now WHERE id = :id")
    suspend fun togglePinnedAt(id: String, now: Long)

    suspend fun delete(id: String) = markDeleted(id, nowMillis())

    @Query("UPDATE notes SET deleted = 1, changedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    /** Sync: every row changed after [since], deleted ones included. */
    @Query("SELECT * FROM notes WHERE changedAt > :since")
    suspend fun changedSince(since: Long): List<Note>

    /** Sync: one row by id, deleted or not (to compare with what the cloud sent). */
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun findById(id: String): Note?
}
