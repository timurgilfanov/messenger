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
            SavedStateHandle(mapOf("email" to "user@example.com", "password" to "Pass123!"))
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.state.test {
            val state = awaitItem()
            assertEquals("user@example.com", state.email)
            assertEquals("Pass123!", state.password)
        }
    }

    @Test
    fun `updateEmail saves email to saved state handle`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.updateEmail("user@example.com")

        assertEquals("user@example.com", handle.get<String>("email"))
    }

    @Test
    fun `updatePassword saves password to saved state handle`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.updatePassword("Pass123!")

        assertEquals("Pass123!", handle.get<String>("password"))
    }
}
