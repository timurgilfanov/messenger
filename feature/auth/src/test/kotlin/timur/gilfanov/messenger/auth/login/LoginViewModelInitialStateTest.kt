package timur.gilfanov.messenger.auth.login

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
class LoginViewModelInitialStateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state has empty fields and no errors`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertEquals("", state.email)
            assertEquals("", state.password)
            assertFalse(state.isLoading)
            assertNull(state.emailError)
            assertNull(state.passwordError)
            assertNull(state.generalError)
        }
    }
}
