package timur.gilfanov.messenger.auth.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
class LoginViewModelProcessDeathTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state restores email from saved state, password starts empty`() = runTest {
        val handle = SavedStateHandle(mapOf(EMAIL_KEY to SAMPLE_EMAIL))
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(SAMPLE_EMAIL, state.email)
            assertEquals("", state.password)
            assertFalse(state.isSubmitEnabled)
        }
    }

    @Test
    fun `updateEmail saves email to saved state handle`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.updateEmail(SAMPLE_EMAIL)

        assertEquals(SAMPLE_EMAIL, handle.get<String>(EMAIL_KEY))
    }

    private companion object {
        const val EMAIL_KEY = "email"
        const val SAMPLE_EMAIL = "user@example.com"
    }
}
