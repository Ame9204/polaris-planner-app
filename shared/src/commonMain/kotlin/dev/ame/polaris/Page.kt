package dev.ame.polaris

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.ame.polaris.ui.PolarisIcons

/**
 * The app's top-level pages, in tab order. Each has its own panel colour (bento style):
 * it's used for the page's header panel, buttons and highlights, and its tab pill.
 */
enum class Page(val label: String, val icon: ImageVector, val color: Color) {
    Overview("Overview", PolarisIcons.Home, Palette.Tangerine),
    Calendar("Calendar", PolarisIcons.Calendar, Palette.Terracotta),
    Tasks("Tasks", PolarisIcons.Tasks, Palette.Sage),
    Schedule("Schedule", PolarisIcons.Schedule, Palette.Tangerine),
    Notes("Notes", PolarisIcons.Notes, Palette.Stone);

    /** "01", "02"… shown in the corner of each page's header, spec-sheet style. */
    val number: String get() = (ordinal + 1).toString().padStart(2, '0')
}

/** The page currently on screen, so shared pieces (like PageHeader) can use its colour and number. */
val LocalPage = staticCompositionLocalOf { Page.Overview }
