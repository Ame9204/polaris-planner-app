package dev.ame.polaris.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Editing or deleting one day, the rest, or all of a repeating block. */
class SeriesTest {

    private val gym = ScheduleBlock("gym", "Gym", LocalTime(7, 0), LocalTime(8, 0), days = AllDays.toBits(), createdAt = 1)
    private val tue = LocalDate(2026, 11, 10)
    private val wed = LocalDate(2026, 11, 11)
    private val mon = LocalDate(2026, 11, 9)
    private val later = BlockFields("Gym (late)", LocalTime(18, 0), LocalTime(19, 0), AllDays.toBits(), 10)

    private var ids = 0
    private fun newId() = "new${++ids}"

    private fun titlesOn(blocks: List<ScheduleBlock>, day: LocalDate) =
        blocks.filter { it.happensOn(day) }.map { it.title }

    @Test
    fun editThisDayOnly() {
        val result = editSeries(gym, tue, SeriesScope.ThisDay, later, ::newId, now = 5)

        assertEquals(listOf("Gym (late)"), titlesOn(result, tue)) // the one-off copy
        assertEquals(listOf("Gym"), titlesOn(result, mon))        // other days untouched
        assertEquals(listOf("Gym"), titlesOn(result, wed))
        assertEquals(LocalTime(18, 0), result.single { it.happensOn(tue) }.start)
    }

    @Test
    fun editThisAndFollowingDays() {
        val result = editSeries(gym, tue, SeriesScope.Following, later, ::newId, now = 5)

        assertEquals(listOf("Gym"), titlesOn(result, mon))        // before: the old series
        assertEquals(listOf("Gym (late)"), titlesOn(result, tue)) // from Tuesday: the new one
        assertEquals(listOf("Gym (late)"), titlesOn(result, LocalDate(2026, 12, 25)))
        assertEquals(2, result.size)
    }

    @Test
    fun editAllDays() {
        val result = editSeries(gym, tue, SeriesScope.All, later, ::newId, now = 5)
        assertEquals(listOf("gym"), result.map { it.id }) // same block, changed in place
        assertEquals(listOf("Gym (late)"), titlesOn(result, mon))
    }

    @Test
    fun followingOnTheFirstDayIsTheSameAsAll() {
        val series = gym.copy(startsOn = tue)
        val result = editSeries(series, tue, SeriesScope.Following, later, ::newId, now = 5)
        assertEquals(listOf("gym"), result.map { it.id })
    }

    @Test
    fun deleteThisDayOnly() {
        val (rows, deleteAll) = deleteFromSeries(gym, tue, SeriesScope.ThisDay)
        assertEquals(false, deleteAll)
        assertTrue(titlesOn(rows, tue).isEmpty())
        assertEquals(listOf("Gym"), titlesOn(rows, wed))
    }

    @Test
    fun deleteThisAndFollowingDays() {
        val (rows, deleteAll) = deleteFromSeries(gym, tue, SeriesScope.Following)
        assertEquals(false, deleteAll)
        assertEquals(listOf("Gym"), titlesOn(rows, mon))
        assertTrue(titlesOn(rows, tue).isEmpty())
        assertTrue(titlesOn(rows, wed).isEmpty())
    }

    @Test
    fun deleteAllDays() {
        val (rows, deleteAll) = deleteFromSeries(gym, tue, SeriesScope.All)
        assertTrue(rows.isEmpty())
        assertEquals(true, deleteAll)
    }

    @Test
    fun skippingTwoDaysKeepsBoth() {
        val twice = gym.skipping(tue).skipping(wed)
        assertEquals(setOf(tue, wed), twice.skippedDays)
        assertEquals(false, twice.happensOn(tue))
        assertEquals(true, twice.happensOn(mon))
    }
}
