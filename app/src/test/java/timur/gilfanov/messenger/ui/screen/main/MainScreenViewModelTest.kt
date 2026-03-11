package timur.gilfanov.messenger.ui.screen.main

import androidx.lifecycle.SavedStateHandle
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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

    @Test
    fun `selected tab persists across process death via SavedStateHandle`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel1 = MainScreenViewModel(savedStateHandle)

        viewModel1.selectTab(1)
        advanceUntilIdle()

        val viewModel2 = MainScreenViewModel(savedStateHandle)

        assertEquals(1, viewModel2.state.value.selectedTab)
    }

    @Test
    fun `default tab is 0 with empty SavedStateHandle`() = runTest {
        val viewModel = MainScreenViewModel(SavedStateHandle())

        assertEquals(0, viewModel.state.value.selectedTab)
    }

    @Test
    fun `selectTab updates selectedTab`() = runTest {
        val viewModel = MainScreenViewModel(SavedStateHandle())

        viewModel.selectTab(1)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.selectedTab)
    }
}
