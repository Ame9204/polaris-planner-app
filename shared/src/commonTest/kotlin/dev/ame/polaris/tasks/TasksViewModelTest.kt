package dev.ame.polaris.tasks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// commonTest runs on every target. Run on desktop with: .\gradlew.bat :shared:jvmTest
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private val dao = FakeTaskDao()

    // viewModelScope runs on the "Main" (UI) thread, which doesn't exist in tests.
    // Swap in a test dispatcher that runs coroutines immediately.
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun addTrimsTitleAndKeepsDueDate() {
        val vm = TasksViewModel(dao)
        vm.add("  Buy milk  ", LocalDate(2026, 11, 12))

        val task = dao.items.value.single()
        assertEquals("Buy milk", task.title)
        assertEquals(LocalDate(2026, 11, 12), task.due)
    }

    @Test
    fun blankTitleIsIgnored() {
        val vm = TasksViewModel(dao)
        vm.add("   ")
        assertTrue(dao.items.value.isEmpty())
    }

    @Test
    fun toggleDoneFlipsOnlyThatTask() {
        val vm = TasksViewModel(dao)
        vm.add("A")
        vm.add("B")
        val a = dao.items.value.first { it.title == "A" }

        vm.toggleDone(a.id)

        assertEquals(mapOf("A" to true, "B" to false), dao.items.value.associate { it.title to it.done })
    }

    @Test
    fun deleteRemovesTask() {
        val vm = TasksViewModel(dao)
        vm.add("A")
        vm.delete(dao.items.value.single().id)
        assertTrue(dao.items.value.isEmpty())
    }

    @Test
    fun tasksFollowsTheDatabase() = runTest {
        val vm = TasksViewModel(dao)
        // `tasks` only watches the database while someone is collecting it, like the screen does.
        // UnconfinedTestDispatcher makes the collector start right away instead of waiting its turn.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.tasks.collect {} }

        vm.add("A")

        assertEquals(listOf("A"), vm.tasks.value.map { it.title })
    }
}
