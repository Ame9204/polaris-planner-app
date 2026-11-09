package dev.ame.polaris

import androidx.compose.ui.graphics.vector.ImageVector
import dev.ame.polaris.ui.PolarisIcons

/** The app's top-level pages, in tab order. */
enum class Page(val label: String, val icon: ImageVector) {
    Overview("Overview", PolarisIcons.Home),
    Calendar("Calendar", PolarisIcons.Calendar),
    Tasks("Tasks", PolarisIcons.Tasks),
    Schedule("Schedule", PolarisIcons.Schedule),
    Notes("Notes", PolarisIcons.Notes),
}
