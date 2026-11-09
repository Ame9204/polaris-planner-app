package dev.ame.polaris.weather

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

// The app's own weather types. @Serializable lets them be saved as JSON in the settings table.

/** A place picked from the search, e.g. Jakarta, Jakarta, Indonesia. */
@Serializable
data class Place(
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    /** "Bandung, West Java, Indonesia" (skips parts that repeat the name). */
    val label: String
        get() = listOfNotNull(name, region?.takeIf { it != name }, country).joinToString(", ")
}

enum class TempUnit(val apiName: String, val symbol: String) {
    Celsius("celsius", "°C"),
    Fahrenheit("fahrenheit", "°F"),
}

/** The latest weather, plus when it was fetched, so it can be shown offline. */
@Serializable
data class WeatherNow(
    val temperature: Double,
    val code: Int,
    val isDay: Boolean,
    val high: Double? = null,
    val low: Double? = null,
    val unit: TempUnit,
    val fetchedAt: Long,
)

/** 23.6 -> "24°" */
fun degrees(value: Double): String = "${value.roundToInt()}°"

/** What a WMO weather code (what Open-Meteo returns) looks like. */
data class Condition(val symbol: String, val description: String)

/** Open-Meteo uses the standard WMO codes: https://open-meteo.com/en/docs (bottom of page). */
fun condition(code: Int, isDay: Boolean): Condition = when (code) {
    0 -> Condition(if (isDay) "☀" else "☾", "Clear")
    1 -> Condition(if (isDay) "☀" else "☾", "Mostly clear")
    2 -> Condition("⛅", "Partly cloudy")
    3 -> Condition("☁", "Cloudy")
    45, 48 -> Condition("≋", "Fog")
    51, 53, 55, 56, 57 -> Condition("☂", "Drizzle")
    61, 63, 65, 66, 67 -> Condition("☂", "Rain")
    71, 73, 75, 77 -> Condition("❄", "Snow")
    80, 81, 82 -> Condition("☂", "Showers")
    85, 86 -> Condition("❄", "Snow showers")
    95, 96, 99 -> Condition("⚡", "Thunderstorm")
    else -> Condition("☁", "Unknown")
}
