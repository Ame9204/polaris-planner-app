package dev.ame.polaris.weather

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeatherApiTest {

    private val berlin = Place("Berlin", "State of Berlin", "Germany", 52.52437, 13.41053)

    @Test
    fun searchParsesPlaces() = runTest {
        var askedFor = ""
        val api = fakeApi { request ->
            askedFor = request.url.parameters["name"].orEmpty()
            json(BerlinSearchJson)
        }

        val places = api.searchPlaces("Berlin")

        assertEquals("Berlin", askedFor)
        assertEquals(2, places.size)
        assertEquals("Berlin, State of Berlin, Germany", places.first().label)
    }

    @Test
    fun searchWithNoMatchesIsEmpty() = runTest {
        // Open-Meteo leaves out "results" entirely when nothing matches.
        val api = fakeApi { json("""{"generationtime_ms":0.2}""") }
        assertTrue(api.searchPlaces("Xyzzy").isEmpty())
    }

    @Test
    fun currentParsesWeatherAndSendsUnit() = runTest {
        var unitSent = ""
        val api = fakeApi { request ->
            unitSent = request.url.parameters["temperature_unit"].orEmpty()
            json(ForecastJson)
        }

        val weather = api.current(berlin, TempUnit.Fahrenheit, fetchedAt = 42)

        assertEquals("fahrenheit", unitSent)
        assertEquals(1.5, weather.temperature)
        assertEquals(3, weather.code)
        assertEquals(false, weather.isDay)
        assertEquals(5.0, weather.high)
        assertEquals(0.7, weather.low)
        assertEquals(42, weather.fetchedAt)
    }

    @Test
    fun conditionsAndDegrees() {
        assertEquals("Cloudy", condition(3, isDay = true).description)
        assertEquals("☾", condition(0, isDay = false).symbol)
        assertEquals("Thunderstorm", condition(95, isDay = true).description)
        assertEquals("2°", degrees(1.5))
        assertEquals("-3°", degrees(-2.6))
    }
}
