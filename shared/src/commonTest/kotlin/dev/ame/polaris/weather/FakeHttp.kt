package dev.ame.polaris.weather

import dev.ame.polaris.data.Setting
import dev.ame.polaris.data.SettingDao
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A WeatherApi whose "internet" is the [handler] you give it: no real network in tests. */
fun fakeApi(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): WeatherApi = WeatherApi(
    HttpClient(MockEngine(handler)) {
        install(ContentNegotiation) { json(WeatherApi.json) }
        expectSuccess = true
    }
)

fun MockRequestHandleScope.json(body: String) =
    respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))

// Trimmed copies of real Open-Meteo responses.
const val BerlinSearchJson = """
{"results":[
  {"id":2950159,"name":"Berlin","latitude":52.52437,"longitude":13.41053,"country":"Germany","admin1":"State of Berlin"},
  {"id":5083330,"name":"Berlin","latitude":44.46867,"longitude":-71.18508,"country":"United States","admin1":"New Hampshire"}
]}"""

const val ForecastJson = """
{"latitude":52.5,"longitude":13.4,"current_units":{"temperature_2m":"°C"},
 "current":{"time":"2026-11-09T03:45","interval":900,"temperature_2m":1.5,"weather_code":3,"is_day":0},
 "daily":{"time":["2026-11-09"],"temperature_2m_max":[5.0],"temperature_2m_min":[0.7]}}"""

/** An in-memory settings table. */
class FakeSettingDao : SettingDao {
    val values = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun observe(key: String): Flow<String?> = values.map { it[key] }
    override suspend fun get(key: String): String? = values.value[key]
    override suspend fun put(setting: Setting) = values.update { it + (setting.key to setting.value) }
    override suspend fun remove(key: String) = values.update { it - key }
}
