package dev.ame.polaris.weather

import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val settings = FakeSettingDao()
    private val berlin = Place("Berlin", "State of Berlin", "Germany", 52.52437, 13.41053)
    private var time = 1_000_000L
    private var online = true
    private var requests = 0

    // A fake internet that can be switched off.
    private val api = fakeApi {
        requests++
        if (online) json(ForecastJson) else respondError(HttpStatusCode.ServiceUnavailable)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.watchedViewModel(): WeatherViewModel {
        val vm = WeatherViewModel(settings, api, clock = { time })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun choosingAPlaceFetchesAndSavesWeather() = runTest {
        val vm = watchedViewModel()
        vm.choosePlace(berlin)

        // The fake HTTP engine answers on its own thread, so wait for the result to arrive.
        val state = vm.state.first { it.weather != null }

        assertEquals("Berlin", state.place?.name)
        assertEquals(1.5, state.weather?.temperature)
    }

    @Test
    fun offlineKeepsShowingTheSavedWeather() = runTest {
        val vm = watchedViewModel()
        vm.choosePlace(berlin)
        vm.state.first { it.weather != null }

        online = false
        vm.refresh()

        val state = vm.state.first { it.offline }
        assertEquals(1.5, state.weather?.temperature) // old weather still there
    }

    @Test
    fun freshWeatherIsNotFetchedAgain() = runTest {
        val vm = watchedViewModel()
        vm.choosePlace(berlin)
        vm.state.first { it.weather != null && !it.refreshing }
        val before = requests

        time += 10 * 60 * 1000 // 10 minutes later: still fresh
        vm.refreshIfStale()
        vm.state.first { !it.refreshing }

        assertEquals(before, requests)
    }

    @Test
    fun noPlaceMeansNoWeather() = runTest {
        val vm = watchedViewModel()
        vm.refreshIfStale()
        assertNull(vm.state.value.weather)
        assertEquals(0, requests)
    }
}
