package timur.gilfanov.messenger.ui.screen.main

import androidx.lifecycle.SavedStateHandle
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class MainScreenStoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Orbit's SavedStateContainerDecorator wraps stateFlow with
    // onEach { savedStateHandle[key] = it }. State is only persisted when
    // someone actively collects — the onEach block runs inside collect().
    //
    // Orbit's test() DSL cannot be used here because it swaps the real
    // container with a TestContainerDecorator, bypassing persistence.
    //
    // Orbit's event loop runs on Dispatchers.Default internally, so
    // advanceUntilIdle() on the test scheduler cannot process intents.
    // first { } waits for the actual state change on Dispatchers.Default
    // and triggers the onEach save via active collection.
    @Test
    fun `selected tab persists across process death via SavedStateHandle`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel1 = MainScreenStore(savedStateHandle)

        viewModel1.selectTab(1)
        viewModel1.container.stateFlow.first { it.selectedTab == 1 }

        val viewModel2 = MainScreenStore(savedStateHandle)

        assertEquals(1, viewModel2.container.stateFlow.value.selectedTab)
    }

    @Test
    fun `default tab is 0 with empty SavedStateHandle`() = runTest {
        val viewModel = MainScreenStore(SavedStateHandle())

        assertEquals(0, viewModel.container.stateFlow.value.selectedTab)
    }
}
