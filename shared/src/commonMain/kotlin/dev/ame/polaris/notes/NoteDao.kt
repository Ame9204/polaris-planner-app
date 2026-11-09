package dev.ame.polaris.notes

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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
        WHERE title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%'
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun observeSearch(query: String): Flow<List<Note>>

    @Upsert
    suspend fun upsert(note: Note)

    /** Saves typing without touching other fields (like `pinned`) that may have changed meanwhile. */
    @Query("UPDATE notes SET title = :title, body = :body, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: String, title: String, body: String, updatedAt: Long)

    @Query("UPDATE notes SET pinned = NOT pinned WHERE id = :id")
    suspend fun togglePinned(id: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)
}
