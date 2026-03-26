package timur.gilfanov.messenger.auth.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.auth.ui.LoginViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.auth.ui.screen.login.LoginBlockingError
import timur.gilfanov.messenger.auth.ui.screen.login.LoginGeneralError
import timur.gilfanov.messenger.auth.ui.screen.login.LoginSideEffects
import timur.gilfanov.messenger.auth.ui.screen.login.LoginSnackbarMessage
import timur.gilfanov.messenger.auth.ui.screen.login.LoginViewModel
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidatorStub
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
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
    fun `repository NetworkNotAvailable emits ShowSnackbar NetworkUnavailable`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitLogin()
            advanceUntilIdle()
            val effect = assertIs<LoginSideEffects.ShowSnackbar>(awaitItem())
            assertIs<LoginSnackbarMessage.NetworkUnavailable>(effect.message)
        }
    }

    @Test
    fun `repository local TemporarilyUnavailable emits ShowSnackbar`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.LocalOperationFailed(
                    LocalStorageError.TemporarilyUnavailable,
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitLogin()
            advanceUntilIdle()
            val effect = assertIs<LoginSideEffects.ShowSnackbar>(awaitItem())
            assertIs<LoginSnackbarMessage.StorageTemporarilyUnavailable>(effect.message)
        }
    }

    @Test
    fun `repository LocalOperationFailed StorageFull sets blockingError StorageFull`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginBlockingError.StorageFull>(viewModel.state.value.blockingError)
    }

    @Test
    fun `repository LocalOperationFailed Corrupted sets blockingError Corrupted`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.LocalOperationFailed(LocalStorageError.Corrupted),
            ),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginBlockingError.StorageCorrupted>(viewModel.state.value.blockingError)
    }

    @Test
    fun `repository LocalOperationFailed ReadOnly sets blockingError ReadOnly`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.LocalOperationFailed(LocalStorageError.ReadOnly),
            ),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginBlockingError.StorageReadOnly>(viewModel.state.value.blockingError)
    }

    @Test
    fun `repository LocalOperationFailed AccessDenied sets blockingError AccessDenied`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.LocalOperationFailed(LocalStorageError.AccessDenied),
            ),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginBlockingError.StorageAccessDenied>(viewModel.state.value.blockingError)
    }

    @Test
    fun `repository LocalOperationFailed UnknownError emits ShowSnackbar`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.LocalOperationFailed(
                    LocalStorageError.UnknownError(RuntimeException("test")),
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitLogin()
            advanceUntilIdle()
            val effect = assertIs<LoginSideEffects.ShowSnackbar>(awaitItem())
            assertIs<LoginSnackbarMessage.StorageTemporarilyUnavailable>(effect.message)
        }
    }

    @Test
    fun `repository InvalidEmail sets generalError InvalidEmail`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.InvalidEmail(EmailValidationError.EmailNotExists),
            ),
        )
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginGeneralError.InvalidEmail>(viewModel.state.value.generalError)
    }

    @Test
    fun `repository RemoteOperationFailed non-network emits ShowSnackbar ServiceUnavailable`() =
        runTest {
            val viewModel = createViewModel(
                loginResult = Failure(
                    LoginRepositoryError.RemoteOperationFailed(RemoteError.Failed.ServiceDown),
                ),
            )

            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.effects.test {
                viewModel.submitLogin()
                advanceUntilIdle()
                val effect = assertIs<LoginSideEffects.ShowSnackbar>(awaitItem())
                assertIs<LoginSnackbarMessage.ServiceUnavailable>(effect.message)
            }
        }

    @Test
    fun `repository Cooldown emits ShowSnackbar TooManyAttempts with remaining duration`() =
        runTest {
            val remaining = 5.minutes
            val viewModel = createViewModel(
                loginResult = Failure(
                    LoginRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.Cooldown(remaining),
                    ),
                ),
            )

            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.effects.test {
                viewModel.submitLogin()
                advanceUntilIdle()
                val effect = assertIs<LoginSideEffects.ShowSnackbar>(awaitItem())
                val message = assertIs<LoginSnackbarMessage.TooManyAttempts>(effect.message)
                assertTrue(message.remaining == remaining)
            }
        }

    @Test
    fun `retryLastAction after credentials login clears blockingError and retries`() = runTest {
        val viewModel = createViewModel(
            loginResult = Failure(
                LoginRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("Password1")
        viewModel.submitLogin()
        advanceUntilIdle()
        assertIs<LoginBlockingError.StorageFull>(viewModel.state.value.blockingError)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.retryLastAction()
            advanceUntilIdle()
            assertNull(viewModel.state.value.blockingError)
            assertIs<LoginSideEffects.NavigateToChatList>(awaitItem())
        }
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

    @Test
    fun `second submitLogin while first is in progress is ignored`() = runTest {
        val deferred = CompletableDeferred<Unit>()
        val viewModel = LoginViewModel(
            loginWithCredentials = {
                deferred.await()
                ResultWithError.Success(Unit)
            },
            loginWithGoogle = { ResultWithError.Success(Unit) },
            savedStateHandle = SavedStateHandle(),
            credentialsValidator = CredentialsValidatorStub(null),
        )
        viewModel.updateEmail("user@example.com")
        viewModel.updatePassword("Password1")

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitLogin()
            runCurrent()
            assertTrue(viewModel.state.value.isLoading)
            viewModel.submitLogin()
            deferred.complete(Unit)
            advanceUntilIdle()
            assertIs<LoginSideEffects.NavigateToChatList>(awaitItem())
            expectNoEvents()
        }
    }
}
