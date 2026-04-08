package timur.gilfanov.messenger.auth.ui

import app.cash.turbine.test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.ui.LoginViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.testutil.MainDispatcherRule

private const val VALID_EMAIL = "user@example.com"
private const val INVALID_EMAIL = "notanemail"
private const val VALID_PASSWORD = "password1"
private const val INVALID_SHORT_PASSWORD = "short"

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LoginViewModelRealTimeValidationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state has no email error`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.emailError)
        }
    }

    @Test
    fun `initial state has no password error`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.passwordError)
        }
    }

    @Test
    fun `entering invalid email sets emailError`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail(INVALID_EMAIL)

        viewModel.state.test {
            val state = awaitItem()
            assertIs<EmailValidationError>(state.emailError)
        }
    }

    @Test
    fun `fixing invalid email clears emailError`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail(INVALID_EMAIL)
        viewModel.updateEmail(VALID_EMAIL)

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.emailError)
        }
    }

    @Test
    fun `entering invalid password sets passwordError`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(INVALID_SHORT_PASSWORD)

        viewModel.state.test {
            val state = awaitItem()
            assertIs<PasswordValidationError.PasswordTooShort>(state.passwordError)
        }
    }

    @Test
    fun `fixing invalid password clears passwordError`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(INVALID_SHORT_PASSWORD)
        viewModel.updatePassword(VALID_PASSWORD)

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.passwordError)
        }
    }

    @Test
    fun `updating email to valid does not trigger passwordError on untouched password`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail(VALID_EMAIL)

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.passwordError)
        }
    }

    @Test
    fun `fixing password while email is invalid clears passwordError`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(INVALID_SHORT_PASSWORD)
        viewModel.updateEmail(INVALID_EMAIL)
        viewModel.updatePassword(VALID_PASSWORD)

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.passwordError)
        }
    }

    @Test
    fun `invalid password sets passwordError even when email is invalid`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail(INVALID_EMAIL)
        viewModel.updatePassword(INVALID_SHORT_PASSWORD)

        viewModel.state.test {
            val state = awaitItem()
            assertIs<PasswordValidationError.PasswordTooShort>(state.passwordError)
        }
    }

    @Test
    fun `fixing email do not hide passwordError`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateEmail(VALID_EMAIL)
        viewModel.updatePassword(VALID_PASSWORD)
        viewModel.updateEmail(INVALID_EMAIL)
        viewModel.updatePassword(INVALID_SHORT_PASSWORD)
        viewModel.updateEmail(VALID_EMAIL)

        viewModel.state.test {
            val state = awaitItem()
            assertIs<PasswordValidationError.PasswordTooShort>(state.passwordError)
        }
    }
}
