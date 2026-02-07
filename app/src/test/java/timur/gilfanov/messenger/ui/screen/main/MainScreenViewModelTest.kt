package timur.gilfanov.messenger.ui.screen.main

import androidx.lifecycle.SavedStateHandle
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class MainScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Orbit's SavedStateContainerDecorator wraps stateFlow with
    // onEach { savedStateHandle[key] = it }. State is only persisted when
    // someone actively collects — the onEach block runs inside collect().
    //
    // Orbit's test() DSL cannot be used here because it swaps the real
    // container with a TestContainerDecorator, bypassing persistence.
    //
    // The collector must be launched BEFORE selectTab so that both the
    // collector subscription and the intent are processed in a single
    // advanceUntilIdle() pass: collector subscribes first (FIFO), then
    // the event loop runs the intent, and the emission reaches the
    // already-active collector whose onEach persists state.
    @Test
    fun `selected tab persists across process death via SavedStateHandle`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel1 = MainScreenViewModel(savedStateHandle)

        val collectJob = launch(mainDispatcherRule.testDispatcher) {
            viewModel1.container.stateFlow.collect { }
        }
        viewModel1.selectTab(1)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        collectJob.cancel()

        val viewModel2 = MainScreenViewModel(savedStateHandle)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel2.container.stateFlow.value.selectedTab)
    }

    @Test
    fun `default tab is 0 with empty SavedStateHandle`() = runTest {
        val viewModel = MainScreenViewModel(SavedStateHandle())

        assertEquals(0, viewModel.container.stateFlow.value.selectedTab)
    }
}
