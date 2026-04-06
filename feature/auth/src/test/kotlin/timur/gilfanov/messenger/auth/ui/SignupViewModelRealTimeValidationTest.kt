package timur.gilfanov.messenger.auth.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.ui.SignupViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.testutil.MainDispatcherRule

private const val VALID_NAME = "Alice"
private const val INVALID_BLANK_NAME = "   "
private const val VALID_EMAIL = "user@example.com"
private const val INVALID_EMAIL = "notanemail"
private const val VALID_PASSWORD = "password1"
private const val INVALID_SHORT_PASSWORD = "short"

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class SignupViewModelRealTimeValidationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `entering invalid name sets nameError`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateName(INVALID_BLANK_NAME)

        viewModel.state.test {
            val state = awaitItem()
            assertIs<ProfileNameValidationError.LengthOutOfBounds>(state.nameError)
        }
    }

    @Test
    fun `fixing invalid name clears nameError`() = runTest {
        val viewModel = createViewModel()

        viewModel.updateName(INVALID_BLANK_NAME)
        viewModel.updateName(VALID_NAME)

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.nameError)
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
    fun `restored name from SavedStateHandle has no nameError before any edits`() = runTest {
        val handle = SavedStateHandle(mapOf("name" to VALID_NAME, "email" to VALID_EMAIL))
        val viewModel = createViewModel(savedStateHandle = handle)

        viewModel.state.test {
            val state = awaitItem()
            assertNull(state.nameError)
            assertNull(state.emailError)
        }
    }
}
