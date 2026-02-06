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

    // Orbit's container.stateFlow is a lazy decorator that wraps the real StateFlow with
    // onEach { savedStateHandle[key] = it }. State is only persisted to SavedStateHandle
    // when someone actively collects from stateFlow — the onEach block runs inside collect().
    //
    // Orbit's test() DSL cannot be used here because it replaces the real container with a
    // TestContainerDecorator, bypassing the SavedStateHandle persistence entirely.
    @Test
    fun `selected tab persists across process death via SavedStateHandle`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel1 = MainScreenViewModel(savedStateHandle)

        // selectTab dispatches intent { reduce { } } on viewModelScope (Dispatchers.Main).
        // advanceUntilIdle() executes the intent so the internal MutableStateFlow updates.
        viewModel1.selectTab(1)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        // Starting collection triggers the lazy stateFlow decorator's onEach block,
        // which writes the already-updated state to SavedStateHandle.
        val collectJob = launch(mainDispatcherRule.testDispatcher) {
            viewModel1.container.stateFlow.collect { }
        }
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        collectJob.cancel()

        // Simulate process death: create a new ViewModel with the same SavedStateHandle.
        // Orbit restores state from savedStateHandle["state"] in the container() factory.
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
