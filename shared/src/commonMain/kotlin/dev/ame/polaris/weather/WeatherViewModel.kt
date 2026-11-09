package dev.ame.polaris.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ame.polaris.data.SettingDao
import dev.ame.polaris.data.put
import dev.ame.polaris.util.nowMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WeatherUiState(
    val place: Place? = null,
    val unit: TempUnit = TempUnit.Celsius,
    /** Last fetched weather. Kept in the database, so it still shows when offline. */
    val weather: WeatherNow? = null,
    val refreshing: Boolean = false,
    /** True when the last refresh failed (e.g. no internet); the cached weather is still shown. */
    val offline: Boolean = false,
    // Location search (in the settings dialog)
    val searching: Boolean = false,
    val results: List<Place> = emptyList(),
    val searchMessage: String? = null,
)

/** Weather older than this is fetched again. */
const val StaleAfterMs = 30 * 60 * 1000L

/**
 * Offline-first: the screen always shows what's saved in the database. Fetching only
 * *updates* the saved copy, so no internet just means slightly old weather, never a blank.
 */
class WeatherViewModel(
    private val settings: SettingDao,
    private val api: WeatherApi = WeatherApi(),
    private val clock: () -> Long = ::nowMillis, // a parameter so tests can control time
) : ViewModel() {

    private object Keys {
        const val PLACE = "weather.place"
        const val UNIT = "weather.unit"
        const val CACHE = "weather.cache"
    }

    private val json = WeatherApi.json

    /** Things that only matter while the app is open (not saved). */
    private data class Transient(
        val refreshing: Boolean = false,
        val offline: Boolean = false,
        val searching: Boolean = false,
        val results: List<Place> = emptyList(),
        val searchMessage: String? = null,
    )

    private val transient = MutableStateFlow(Transient())

    val state: StateFlow<WeatherUiState> = combine(
        settings.observe(Keys.PLACE).map { it?.let { s -> json.decodeFromString<Place>(s) } },
        settings.observe(Keys.UNIT).map { it?.let(TempUnit::valueOf) ?: TempUnit.Celsius },
        settings.observe(Keys.CACHE).map { it?.let { s -> json.decodeFromString<WeatherNow>(s) } },
        transient,
    ) { place, unit, weather, t ->
        WeatherUiState(place, unit, weather, t.refreshing, t.offline, t.searching, t.results, t.searchMessage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeatherUiState())

    // Only one fetch at a time, so a slow old request can't overwrite a newer one.
    private val fetchLock = Mutex()

    /** Cheap to call often: only fetches when there's no weather, it's old, or the unit changed. */
    fun refreshIfStale() {
        viewModelScope.launch {
            val place = loadPlace() ?: return@launch
            val cached = loadCache()
            val unit = loadUnit()
            if (cached == null || cached.unit != unit || clock() - cached.fetchedAt > StaleAfterMs) {
                fetch(place, unit)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val place = loadPlace() ?: return@launch
            fetch(place, loadUnit())
        }
    }

    fun search(name: String) {
        val query = name.trim()
        if (query.length < 2) return
        viewModelScope.launch {
            transient.update { it.copy(searching = true, searchMessage = null) }
            try {
                val found = api.searchPlaces(query)
                transient.update {
                    it.copy(searching = false, results = found, searchMessage = if (found.isEmpty()) "No places found." else null)
                }
            } catch (e: CancellationException) {
                throw e // never swallow cancellation: it's how coroutines stop
            } catch (e: Exception) {
                transient.update { it.copy(searching = false, searchMessage = "Couldn't search. Are you online?") }
            }
        }
    }

    fun choosePlace(place: Place) {
        viewModelScope.launch {
            settings.put(Keys.PLACE, json.encodeToString(place))
            settings.remove(Keys.CACHE) // don't show the old city's weather under the new name
            transient.update { it.copy(results = emptyList(), searchMessage = null) }
            fetch(place, loadUnit())
        }
    }

    fun setUnit(unit: TempUnit) {
        viewModelScope.launch {
            settings.put(Keys.UNIT, unit.name)
            loadPlace()?.let { fetch(it, unit) }
        }
    }

    private suspend fun fetch(place: Place, unit: TempUnit) = fetchLock.withLock {
        transient.update { it.copy(refreshing = true) }
        try {
            val weather = api.current(place, unit, fetchedAt = clock())
            settings.put(Keys.CACHE, json.encodeToString(weather))
            transient.update { it.copy(refreshing = false, offline = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            transient.update { it.copy(refreshing = false, offline = true) }
        }
    }

    private suspend fun loadPlace(): Place? = settings.get(Keys.PLACE)?.let { json.decodeFromString<Place>(it) }
    private suspend fun loadUnit(): TempUnit = settings.get(Keys.UNIT)?.let(TempUnit::valueOf) ?: TempUnit.Celsius
    private suspend fun loadCache(): WeatherNow? = settings.get(Keys.CACHE)?.let { json.decodeFromString<WeatherNow>(it) }

    override fun onCleared() {
        api.close()
    }
}
