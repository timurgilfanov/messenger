package timur.gilfanov.messenger.auth.ui

import app.cash.turbine.test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.ui.LoginViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LoginViewModelButtonStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `both empty - button disabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.isSubmitEnabled)
        }
    }

    @Test
    fun `valid email only - button disabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail("user@example.com")

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.isSubmitEnabled)
        }
    }

    @Test
    fun `valid password only - button disabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.updatePassword("password1")

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.isSubmitEnabled)
        }
    }

    @Test
    fun `both valid - button enabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password1")

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isSubmitEnabled)
        }
    }

    @Test
    fun `loading state - button disabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password1")

        viewModel.state.test {
            skipItems(1)

            viewModel.submitLogin()

            val loadingState = awaitItem()
            assertFalse(loadingState.isSubmitEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
