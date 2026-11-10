package dev.ame.polaris.schedule

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * A fixed block of time in your day, like "Gym 07:00–08:00".
 * It either repeats on some weekdays ([days] != 0) or happens once on [date].
 */
@Entity(tableName = "schedule_blocks")
data class ScheduleBlock(
    @PrimaryKey val id: String,
    val title: String,
    val start: LocalTime,
    val end: LocalTime,
    /**
     * Which weekdays it repeats on, one bit per day: Mon=1, Tue=2, Wed=4, Thu=8,
     * Fri=16, Sat=32, Sun=64. Weekdays = 31, every day = 127, 0 = doesn't repeat.
     * See ScheduleLogic.kt for the helpers that read and write this.
     */
    val days: Int = 0,
    /** For a one-off block: the day it happens. Null for repeating blocks. */
    val date: LocalDate? = null,
    val createdAt: Long,
    /**
     * Remind this many minutes before the block starts: 0 = when it starts, null = no reminder.
     * defaultValue = "0": blocks made before this setting existed keep reminding at start.
     */
    @ColumnInfo(defaultValue = "0")
    val remindMinutesBefore: Int? = 0,
    // ---- Sync bookkeeping (the app's screens never show these) ----
    /** When this row last changed, on any device (ms since 1970). The newest change wins. */
    @ColumnInfo(defaultValue = "0")
    val changedAt: Long = 0,
    /** Deleted rows stay as a marker, so the deletion can reach your other devices. */
    @ColumnInfo(defaultValue = "0")
    val deleted: Boolean = false,
) {
    val repeats: Boolean get() = days != 0
}
