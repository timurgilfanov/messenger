package timur.gilfanov.messenger.auth.login

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.login.LoginViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LoginViewModelProcessDeathTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state restores email and password from saved state`() = runTest {
        val handle =
            SavedStateHandle(mapOf(EMAIL_KEY to SAMPLE_EMAIL, PASSWORD_KEY to SAMPLE_PASSWORD))
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals(SAMPLE_EMAIL, state.email)
            assertEquals(SAMPLE_PASSWORD, state.password)
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
    fun `updatePassword saves password to saved state handle`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.updatePassword(SAMPLE_PASSWORD)

        assertEquals(SAMPLE_PASSWORD, handle.get<String>(PASSWORD_KEY))
    }

    private companion object {
        const val EMAIL_KEY = "email"
        const val PASSWORD_KEY = "password"
        const val SAMPLE_EMAIL = "user@example.com"
        const val SAMPLE_PASSWORD = "Pass123!"
    }
}
