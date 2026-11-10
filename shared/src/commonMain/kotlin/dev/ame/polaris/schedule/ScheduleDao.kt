package dev.ame.polaris.schedule

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.ame.polaris.util.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

@Dao
interface ScheduleDao {

    /**
     * Blocks happening on [date]: one-offs on that date, plus repeating blocks whose
     * [ScheduleBlock.days] has [dayBit] set. `&` is SQL's bitwise AND.
     */
    @Query(
        """
        SELECT * FROM schedule_blocks
        WHERE deleted = 0 AND (date = :date OR (days & :dayBit) != 0)
        ORDER BY start, createdAt
        """
    )
    fun observeForDayRaw(date: LocalDate, dayBit: Int): Flow<List<ScheduleBlock>>

    /** Blocks on [date], with each series' start/end and skipped days applied. */
    fun observeForDay(date: LocalDate, dayBit: Int): Flow<List<ScheduleBlock>> =
        observeForDayRaw(date, dayBit).map { list -> list.filter { it.happensOn(date) } }

    /**
     * Everything that could happen between [from] and [to]: all repeating blocks, plus
     * one-offs in that range. The week view then sorts them into days (see [happensOn]).
     */
    @Query(
        """
        SELECT * FROM schedule_blocks
        WHERE deleted = 0 AND (days != 0 OR date BETWEEN :from AND :to)
        ORDER BY start, createdAt
        """
    )
    fun observeForRangeRaw(from: LocalDate, to: LocalDate): Flow<List<ScheduleBlock>>

    /** Every block that could happen in the range; callers check each day with [happensOn]. */
    fun observeForRange(from: LocalDate, to: LocalDate): Flow<List<ScheduleBlock>> = observeForRangeRaw(from, to)


    // Writes go through the plain-named functions (upsert, delete, toggle…), which stamp
    // `changedAt` with the current time so sync knows what changed. The "…At"/"Raw" ones are
    // what Room actually runs; the sync engine uses upsertRaw to store rows from the cloud
    // exactly as they are.

    suspend fun upsert(block: ScheduleBlock) = upsertRaw(block.copy(changedAt = nowMillis(), deleted = false))

    @Upsert
    suspend fun upsertRaw(block: ScheduleBlock)

    suspend fun delete(id: String) = markDeleted(id, nowMillis())

    @Query("UPDATE schedule_blocks SET deleted = 1, changedAt = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    /** Sync: every row changed after [since], deleted ones included. */
    @Query("SELECT * FROM schedule_blocks WHERE changedAt > :since")
    suspend fun changedSince(since: Long): List<ScheduleBlock>

    /** Sync: one row by id, deleted or not (to compare with what the cloud sent). */
    @Query("SELECT * FROM schedule_blocks WHERE id = :id")
    suspend fun findById(id: String): ScheduleBlock?
}
