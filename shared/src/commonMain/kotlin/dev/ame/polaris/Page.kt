package dev.ame.polaris

/** The app's top-level pages, in tab order. */
enum class Page(val label: String, val symbol: String) {
    Overview("Overview", "☼"),
    Calendar("Calendar", "▦"),
    Tasks("Tasks", "✓"),
    Schedule("Schedule", "◷"),
    Notes("Notes", "✎"),
}
