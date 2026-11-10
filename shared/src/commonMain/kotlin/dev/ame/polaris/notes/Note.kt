package dev.ame.polaris.notes

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One note. [body] is Markdown text. */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val createdAt: Long,
    /** Last edit time; the list shows the most recently edited notes first. */
    val updatedAt: Long,
    // ---- Sync bookkeeping (the app's screens never show these) ----
    /** When this row last changed, on any device (ms since 1970). The newest change wins. */
    @ColumnInfo(defaultValue = "0")
    val changedAt: Long = 0,
    /** Deleted rows stay as a marker, so the deletion can reach your other devices. */
    @ColumnInfo(defaultValue = "0")
    val deleted: Boolean = false,
) {
    val isBlank: Boolean get() = title.isBlank() && body.isBlank()
}
