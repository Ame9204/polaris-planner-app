package dev.ame.polaris.notes

import dev.ame.polaris.util.hhmm
import dev.ame.polaris.util.shortDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// Pure logic for the Notes page: no UI, no database, easy to test.

private val leadingMarkdown = Regex("""^\s*(#{1,6}\s+|[-*+]\s+(\[[ xX]]\s+)?|>\s*|\d+[.)]\s+)""")
private val inlineMarkdown = Regex("""(\*\*|__|\*|_|`|~~)""")

/**
 * The first line of text in [body], without Markdown symbols, for the list preview.
 * "## **Shopping** list" -> "Shopping list"
 */
fun previewText(body: String, maxLength: Int = 80): String {
    val line = body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return ""
    val plain = line.replace(leadingMarkdown, "").replace(inlineMarkdown, "").trim()
    return if (plain.length > maxLength) plain.take(maxLength).trimEnd() + "…" else plain
}

/** The title to show in the list. Untitled notes use their first line instead. */
fun displayTitle(note: Note): String = when {
    note.title.isNotBlank() -> note.title.trim()
    note.body.isNotBlank() -> previewText(note.body, maxLength = 40)
    else -> "Untitled"
}

/** "14:05" for edits made today, otherwise "12 Nov". */
fun updatedLabel(millis: Long, today: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val at = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    return if (at.date == today) hhmm(at.time) else shortDate(at.date)
}
