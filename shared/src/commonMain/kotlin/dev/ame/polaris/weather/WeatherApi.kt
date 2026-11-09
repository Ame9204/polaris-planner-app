package dev.ame.polaris.weather

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Talks to Open-Meteo (free, no account or API key).
 * Only the city name you search for, and the chosen place's coordinates, are sent.
 *
 * The HttpClient is passed in so tests can give it a fake engine that returns canned JSON.
 */
class WeatherApi(private val client: HttpClient = defaultClient()) {

    /** Places matching [name], best match first. */
    suspend fun searchPlaces(name: String): List<Place> {
        val response: GeoResponse = client.get("https://geocoding-api.open-meteo.com/v1/search") {
            parameter("name", name)
            parameter("count", 8)
            parameter("language", "en")
            parameter("format", "json")
        }.body()
        return response.results.map {
            Place(it.name, it.admin1, it.country, it.latitude, it.longitude)
        }
    }

    /** Current conditions and today's high/low for [place]. */
    suspend fun current(place: Place, unit: TempUnit, fetchedAt: Long): WeatherNow {
        val response: ForecastResponse = client.get("https://api.open-meteo.com/v1/forecast") {
            parameter("latitude", place.latitude)
            parameter("longitude", place.longitude)
            parameter("current", "temperature_2m,weather_code,is_day")
            parameter("daily", "temperature_2m_max,temperature_2m_min")
            parameter("timezone", "auto")
            parameter("forecast_days", 1)
            parameter("temperature_unit", unit.apiName)
        }.body()
        return WeatherNow(
            temperature = response.current.temperature,
            code = response.current.weatherCode,
            isDay = response.current.isDay == 1,
            high = response.daily?.max?.firstOrNull(),
            low = response.daily?.min?.firstOrNull(),
            unit = unit,
            fetchedAt = fetchedAt,
        )
    }

    fun close() = client.close()

    companion object {
        /** JSON that ignores fields we don't use, so API additions never break the app. */
        val json = Json { ignoreUnknownKeys = true }

        /** No engine given: Ktor uses whichever is on the platform (OkHttp on Android, Java on desktop). */
        fun defaultClient() = HttpClient {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) { requestTimeoutMillis = 10_000 }
            expectSuccess = true // HTTP errors (404, 500…) become exceptions
        }
    }
}

// ---- Shapes of Open-Meteo's JSON (only the fields we read) ----

@Serializable
internal data class GeoResponse(val results: List<GeoResult> = emptyList())

@Serializable
internal data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

@Serializable
internal data class ForecastResponse(val current: Current, val daily: Daily? = null)

@Serializable
internal data class Current(
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("is_day") val isDay: Int = 1,
)

@Serializable
internal data class Daily(
    @SerialName("temperature_2m_max") val max: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val min: List<Double?> = emptyList(),
)
