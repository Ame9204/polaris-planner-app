package dev.ame.polaris.schedule

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
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
        WHERE date = :date OR (days & :dayBit) != 0
        ORDER BY start, createdAt
        """
    )
    fun observeForDay(date: LocalDate, dayBit: Int): Flow<List<ScheduleBlock>>

    /**
     * Everything that could happen between [from] and [to]: all repeating blocks, plus
     * one-offs in that range. The week view then sorts them into days (see [happensOn]).
     */
    @Query(
        """
        SELECT * FROM schedule_blocks
        WHERE days != 0 OR date BETWEEN :from AND :to
        ORDER BY start, createdAt
        """
    )
    fun observeForRange(from: LocalDate, to: LocalDate): Flow<List<ScheduleBlock>>

    @Upsert
    suspend fun upsert(block: ScheduleBlock)

    @Query("DELETE FROM schedule_blocks WHERE id = :id")
    suspend fun delete(id: String)
}
