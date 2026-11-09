package dev.ame.polaris.data

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.ame.polaris.calendar.CalendarEvent
import dev.ame.polaris.calendar.EventDao
import dev.ame.polaris.notes.Note
import dev.ame.polaris.notes.NoteDao
import dev.ame.polaris.schedule.ScheduleBlock
import dev.ame.polaris.schedule.ScheduleDao
import dev.ame.polaris.tasks.Task
import dev.ame.polaris.tasks.TaskDao
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

/**
 * The app's single local database. Every feature adds its tables to `entities`
 * and a DAO function here.
 *
 * Changing a table? Bump `version` and add an AutoMigration(from = old, to = new).
 * Room compares the saved schemas in shared/schemas/ and writes the upgrade SQL,
 * so existing data on your devices is kept.
 *
 * History:
 *  1: tasks
 *  2: + events (Calendar)
 *  3: + schedule_blocks (Schedule)
 *  4: + notes (Notes)
 *  5: + settings (key/value: weather location, units, cached weather)
 *  6: + tasks.remindAt, schedule_blocks.remindMinutesBefore (per-item reminders)
 */
@Database(
    entities = [Task::class, CalendarEvent::class, ScheduleBlock::class, Note::class, Setting::class],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
    ],
)
@TypeConverters(Converters::class)
@ConstructedBy(PolarisDatabaseConstructor::class)
abstract class PolarisDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun eventDao(): EventDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun noteDao(): NoteDao
    abstract fun settingDao(): SettingDao
}

/** Room generates the `actual` for each platform. You never write it yourself. */
@Suppress("KotlinNoActualForExpect")
expect object PolarisDatabaseConstructor : RoomDatabaseConstructor<PolarisDatabase> {
    override fun initialize(): PolarisDatabase
}

/**
 * SQLite has no date or time types, so they're stored as text:
 * dates like "2026-11-12", times like "09:30".
 */
class Converters {
    @TypeConverter
    fun fromDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toDate(text: String?): LocalDate? = text?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromTime(time: LocalTime?): String? = time?.toString()

    @TypeConverter
    fun toTime(text: String?): LocalTime? = text?.let { LocalTime.parse(it) }

    /** Date and time together, like "2026-11-12T14:30". */
    @TypeConverter
    fun fromDateTime(dateTime: LocalDateTime?): String? = dateTime?.toString()

    @TypeConverter
    fun toDateTime(text: String?): LocalDateTime? = text?.let { LocalDateTime.parse(it) }
}

/** Shared finishing step; each platform only decides *where* the file lives. */
internal fun RoomDatabase.Builder<PolarisDatabase>.buildPolaris(): PolarisDatabase =
    setDriver(BundledSQLiteDriver()).build()
