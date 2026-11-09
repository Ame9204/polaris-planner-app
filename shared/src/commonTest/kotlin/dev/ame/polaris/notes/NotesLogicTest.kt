package dev.ame.polaris.notes

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesLogicTest {

    @Test
    fun previewStripsMarkdown() {
        assertEquals("Shopping list", previewText("\n\n## **Shopping** list\n- milk"))
        assertEquals("milk", previewText("- milk"))
        assertEquals("call mum", previewText("- [ ] call `mum`"))
        assertEquals("quote", previewText("> quote"))
        assertEquals("first", previewText("1. first"))
    }

    @Test
    fun previewIsShortened() {
        assertEquals("abcde…", previewText("abcdefghij", maxLength = 5))
    }

    @Test
    fun untitledNotesUseTheirFirstLine() {
        assertEquals("Ideas", displayTitle(Note("1", title = "Ideas", body = "# Other", createdAt = 0, updatedAt = 0)))
        assertEquals("Other", displayTitle(Note("1", title = " ", body = "# Other", createdAt = 0, updatedAt = 0)))
        assertEquals("Untitled", displayTitle(Note("1", createdAt = 0, updatedAt = 0)))
    }

    @Test
    fun updatedShowsTimeTodayAndDateOtherwise() {
        val zone = TimeZone.UTC
        val today = LocalDate(2026, 11, 9)
        val thisAfternoon = LocalDateTime(2026, 11, 9, 14, 5).toInstant(zone).toEpochMilliseconds()
        val lastWeek = LocalDateTime(2026, 11, 2, 9, 0).toInstant(zone).toEpochMilliseconds()

        assertEquals("14:05", updatedLabel(thisAfternoon, today, zone))
        assertEquals("2 Nov", updatedLabel(lastWeek, today, zone))
    }
}
