package dev.ame.polaris.notes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dao = FakeNoteDao()

    // Tests share one virtual clock, so `delay(300)` in autosave takes no real time:
    // advanceTimeBy / advanceUntilIdle move the clock forward instantly.
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.watchedViewModel(): NotesViewModel {
        val vm = NotesViewModel(dao)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun newNoteIsCreatedAndOpened() = runTest {
        val vm = watchedViewModel()
        vm.create()
        val note = dao.items.value.single()
        assertEquals(note.id, vm.state.value.selected?.id)
    }

    @Test
    fun typingIsSavedOnlyAfterAPause() = runTest {
        val vm = watchedViewModel()
        vm.create()
        val id = dao.items.value.single().id

        vm.edit(id, "Ide", "")
        vm.edit(id, "Ideas", "")
        advanceTimeBy(SaveDelayMs - 50)
        assertEquals("", dao.items.value.single().title) // still typing: not saved yet

        advanceUntilIdle()
        assertEquals("Ideas", dao.items.value.single().title) // only the final text is written
    }

    @Test
    fun leavingAnEmptyNoteDeletesIt() = runTest {
        val vm = watchedViewModel()
        vm.create()
        vm.close()
        advanceUntilIdle()
        assertTrue(dao.items.value.isEmpty())
        assertNull(vm.state.value.selectedId)
    }

    @Test
    fun leavingANoteWithTextKeepsIt() = runTest {
        val vm = watchedViewModel()
        vm.create()
        val id = dao.items.value.single().id
        vm.edit(id, "", "some text")
        vm.close() // even before the autosave timer fires
        advanceUntilIdle()
        assertEquals("some text", dao.items.value.single().body)
    }

    @Test
    fun pinnedNotesComeFirst() = runTest {
        dao.upsert(Note("old", title = "Old but pinned", pinned = true, createdAt = 1, updatedAt = 1))
        dao.upsert(Note("new", title = "New", createdAt = 2, updatedAt = 2))
        val vm = watchedViewModel()
        assertEquals(listOf("old", "new"), vm.state.value.notes.map { it.id })
    }

    @Test
    fun searchFiltersByTitleAndBody() = runTest {
        dao.upsert(Note("1", title = "Groceries", body = "milk, eggs", createdAt = 1, updatedAt = 1))
        dao.upsert(Note("2", title = "Ideas", body = "app about MILK", createdAt = 2, updatedAt = 2))
        dao.upsert(Note("3", title = "Books", createdAt = 3, updatedAt = 3))
        val vm = watchedViewModel()

        vm.setQuery("milk")

        assertEquals(setOf("1", "2"), vm.state.value.notes.map { it.id }.toSet())
    }
}
