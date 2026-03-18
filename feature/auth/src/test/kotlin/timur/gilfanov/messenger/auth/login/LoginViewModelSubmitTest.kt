package timur.gilfanov.messenger.auth.login

import app.cash.turbine.test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.login.LoginViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LoginViewModelSubmitTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `submit with valid credentials emits NavigateToChatList`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("Password1")

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitLogin()
            advanceUntilIdle()
            assertIs<LoginSideEffects.NavigateToChatList>(awaitItem())
        }
    }

    @Test
    fun `submit with blank email sets emailError`() = runTest {
        val viewModel = createViewModel(
            validatorError = CredentialsValidationError.BlankEmail,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitLogin()
            advanceUntilIdle()
            assertIs<CredentialsValidationError.BlankEmail>(viewModel.state.value.emailError)
            expectNoEvents()
        }
    }

    @Test
    fun `submit with short password sets passwordError`() = runTest {
        val viewModel = createViewModel(
            validatorError = CredentialsValidationError.PasswordTooShort(8),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitLogin()
            advanceUntilIdle()
            assertIs<CredentialsValidationError.PasswordTooShort>(
                viewModel.state.value.passwordError,
            )
            expectNoEvents()
        }
    }

    @Test
    fun `updateEmail clears emailError`() = runTest {
        val viewModel = createViewModel(
            validatorError = CredentialsValidationError.BlankEmail,
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        viewModel.updateEmail("user@example.com")
        assertNull(viewModel.state.value.emailError)
    }

    @Test
    fun `updatePassword clears passwordError`() = runTest {
        val viewModel = createViewModel(
            validatorError = CredentialsValidationError.PasswordTooShort(8),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        viewModel.updatePassword("Password1")
        assertNull(viewModel.state.value.passwordError)
    }

    @Test
    fun `repository InvalidCredentials sets generalError InvalidCredentials`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(LoginRepositoryError.InvalidCredentials),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginGeneralError.InvalidCredentials>(viewModel.state.value.generalError)
    }

    @Test
    fun `repository EmailNotVerified sets generalError EmailNotVerified`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(LoginRepositoryError.EmailNotVerified),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginGeneralError.EmailNotVerified>(viewModel.state.value.generalError)
    }

    @Test
    fun `repository AccountSuspended sets generalError AccountSuspended`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(LoginRepositoryError.AccountSuspended),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginGeneralError.AccountSuspended>(viewModel.state.value.generalError)
    }

    @Test
    fun `repository NetworkNotAvailable sets generalError NetworkUnavailable`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
            ),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginGeneralError.NetworkUnavailable>(viewModel.state.value.generalError)
    }

    @Test
    fun `repository LocalOperationFailed sets generalError ServiceUnavailable`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.LocalOperationFailed(LocalStorageError.TemporarilyUnavailable),
            ),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginGeneralError.ServiceUnavailable>(viewModel.state.value.generalError)
    }

    @Test
    fun `isLoading is true while submit is in progress`() = runTest {
        val viewModel = createViewModel()
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("Password1")

        viewModel.state.test {
            awaitItem()
            viewModel.submitLogin()
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
