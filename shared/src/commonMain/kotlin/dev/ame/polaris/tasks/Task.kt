package dev.ame.polaris.tasks

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * One task, and also one row in the `tasks` table (that's what @Entity means).
 * Tasks with a [due] date will show on the Calendar later; tasks without one won't.
 */
@Entity(tableName = "tasks")
data class Task(
    // A random UUID string instead of 1, 2, 3...: when phone and desktop both
    // create tasks offline, their ids can never clash during cloud sync.
    @PrimaryKey val id: String,
    val title: String,
    val done: Boolean = false,
    val due: LocalDate? = null,
    /** Milliseconds since 1970; keeps tasks in the order you added them. */
    val createdAt: Long,
    /**
     * Your own "remind me at" time, if you set one. It replaces the default
     * reminder on the due date. Works for tasks without a due date too.
     */
    val remindAt: LocalDateTime? = null,
)
