package timur.gilfanov.messenger.auth.ui

import app.cash.turbine.test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.ui.SignupViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class SignupViewModelButtonStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `all empty - both buttons disabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.isGoogleSubmitEnabled)
            assertFalse(state.isCredentialsSubmitEnabled)
        }
    }

    @Test
    fun `valid name only - only Google enabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateName("Alice")

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isGoogleSubmitEnabled)
            assertFalse(state.isCredentialsSubmitEnabled)
        }
    }

    @Test
    fun `valid credentials but no name - both disabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password1")

        viewModel.state.test {
            val state = awaitItem()
            assertFalse(state.isGoogleSubmitEnabled)
            assertFalse(state.isCredentialsSubmitEnabled)
        }
    }

    @Test
    fun `valid name and valid email but invalid password - only Google enabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateName("Alice")
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("short")

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isGoogleSubmitEnabled)
            assertFalse(state.isCredentialsSubmitEnabled)
        }
    }

    @Test
    fun `all valid - both buttons enabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateName("Alice")
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password1")
        viewModel.updateConfirmPassword("password1")

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isGoogleSubmitEnabled)
            assertTrue(state.isCredentialsSubmitEnabled)
        }
    }

    @Test
    fun `loading state - both buttons disabled`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateName("Alice")
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password1")
        viewModel.updateConfirmPassword("password1")

        viewModel.state.test {
            skipItems(1)

            viewModel.submitSignupWithCredentials()

            val loadingState = awaitItem()
            assertFalse(loadingState.isGoogleSubmitEnabled)
            assertFalse(loadingState.isCredentialsSubmitEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `both buttons re-enabled after loading completes`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateName("Alice")
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password1")
        viewModel.updateConfirmPassword("password1")

        viewModel.state.test {
            skipItems(1)

            viewModel.submitSignupWithCredentials()

            val loadingState = awaitItem()
            assertFalse(loadingState.isGoogleSubmitEnabled)
            assertFalse(loadingState.isCredentialsSubmitEnabled)

            advanceUntilIdle()

            val finalState = awaitItem()
            assertTrue(finalState.isGoogleSubmitEnabled)
            assertTrue(finalState.isCredentialsSubmitEnabled)
        }
    }

    @Test
    fun `valid credentials with mismatched confirm password - credentials button disabled`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.updateName("Alice")
            viewModel.updateEmail("user@example.com")
            viewModel.updatePassword("password1")
            viewModel.updateConfirmPassword("password2")

            viewModel.state.test {
                val state = awaitItem()
                assertTrue(state.isGoogleSubmitEnabled)
                assertFalse(state.isCredentialsSubmitEnabled)
            }
        }

    @Test
    fun `valid credentials after confirm password matches - credentials button enabled`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.updateName("Alice")
            viewModel.updateEmail("user@example.com")
            viewModel.updatePassword("password1")
            viewModel.updateConfirmPassword("password2")
            viewModel.updateConfirmPassword("password1")

            viewModel.state.test {
                val state = awaitItem()
                assertTrue(state.isGoogleSubmitEnabled)
                assertTrue(state.isCredentialsSubmitEnabled)
            }
        }

    @Test
    fun `credentials button disabled when password changes to not match confirm password`() =
        runTest {
            val viewModel = createViewModel()

            viewModel.updateName("Alice")
            viewModel.updateEmail("user@example.com")
            viewModel.updatePassword("password1")
            viewModel.updateConfirmPassword("password1")
            viewModel.updatePassword("password2")

            viewModel.state.test {
                val state = awaitItem()
                assertTrue(state.isGoogleSubmitEnabled)
                assertFalse(state.isCredentialsSubmitEnabled)
            }
        }

    @Test
    fun `credentials button disabled when confirm password cleared after match`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateName("Alice")
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("password1")
        viewModel.updateConfirmPassword("password1")
        viewModel.updateConfirmPassword("")

        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.isGoogleSubmitEnabled)
            assertFalse(state.isCredentialsSubmitEnabled)
        }
    }
}
