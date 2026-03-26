package timur.gilfanov.messenger.auth.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.ui.SignupViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class SignupViewModelProcessDeathTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state restores name from saved state`() = runTest {
        val handle = SavedStateHandle(mapOf(NAME_KEY to SAMPLE_NAME))
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(SAMPLE_NAME, state.name)
        }
    }

    @Test
    fun `updateName saves name to saved state handle`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.updateName(SAMPLE_NAME)

        assertEquals(SAMPLE_NAME, handle.get<String>(NAME_KEY))
    }

    @Test
    fun `initial state restores email from saved state`() = runTest {
        val handle = SavedStateHandle(mapOf(EMAIL_KEY to SAMPLE_EMAIL))
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(SAMPLE_EMAIL, state.email)
        }
    }

    @Test
    fun `updateEmail saves email to saved state handle`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.updateEmail(SAMPLE_EMAIL)

        assertEquals(SAMPLE_EMAIL, handle.get<String>(EMAIL_KEY))
    }

    @Test
    fun `saved state handle does not store password`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.updatePassword(SAMPLE_PASSWORD)
        viewModel.updateConfirmPassword(SAMPLE_PASSWORD)

        assertEquals(0, handle.keys().size)
    }

    private companion object {
        const val NAME_KEY = "name"
        const val EMAIL_KEY = "email"
        const val SAMPLE_NAME = "Alice Smith"
        const val SAMPLE_EMAIL = "alice@example.com"

        const val SAMPLE_PASSWORD = "Password1"
    }
}
