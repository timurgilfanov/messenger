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
import timur.gilfanov.messenger.auth.ui.SignupViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupBlockingError
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupGeneralError
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupSideEffects
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupSnackbarMessage
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupViewModel
import timur.gilfanov.messenger.auth.validation.CredentialsValidatorImpl
import timur.gilfanov.messenger.auth.validation.ProfileNameValidatorImpl
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class SignupViewModelCredentialsSignupTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `credentials signup success emits NavigateToChatList`() = runTest {
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithCredentials()
            advanceUntilIdle()
            assertIs<SignupSideEffects.NavigateToChatList>(awaitItem())
        }
    }

    @Test
    fun `ValidationFailed email error sets emailError`() = runTest {
        val viewModel = createViewModel(
            credentialsValidatorError = CredentialsValidationError.BlankEmail,
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithCredentials()
            advanceUntilIdle()
            assertIs<CredentialsValidationError.BlankEmail>(viewModel.state.value.emailError)
            expectNoEvents()
        }
    }

    @Test
    fun `ValidationFailed password error sets passwordError`() = runTest {
        val viewModel = createViewModel(
            credentialsValidatorError = CredentialsValidationError.PasswordTooShort(8),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithCredentials()
            advanceUntilIdle()
            assertIs<CredentialsValidationError.PasswordTooShort>(
                viewModel.state.value.passwordError,
            )
            expectNoEvents()
        }
    }

    @Test
    fun `InvalidName client-side sets nameError`() = runTest {
        val nameError = ProfileNameValidationError.LengthOutOfBounds(length = 0, min = 1, max = 50)
        val viewModel = createViewModel(nameValidatorError = nameError)

        viewModel.submitSignupWithCredentials()
        advanceUntilIdle()

        assertIs<ProfileNameValidationError.LengthOutOfBounds>(viewModel.state.value.nameError)
    }

    @Test
    fun `InvalidName server-side sets nameError`() = runTest {
        val nameError = ProfileNameValidationError.UnknownRuleViolation("bad name")
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(SignupRepositoryError.InvalidName(nameError)),
        )

        viewModel.submitSignupWithCredentials()
        advanceUntilIdle()

        assertIs<ProfileNameValidationError.UnknownRuleViolation>(viewModel.state.value.nameError)
    }

    @Test
    fun `InvalidEmail server sets generalError InvalidEmail`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.InvalidEmail(EmailValidationError.EmailTaken),
            ),
        )

        viewModel.submitSignupWithCredentials()
        advanceUntilIdle()

        assertIs<SignupGeneralError.InvalidEmail>(viewModel.state.value.generalError)
    }

    @Test
    fun `InvalidPassword server sets generalError InvalidPassword`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.InvalidPassword(PasswordValidationError.PasswordTooShort(8)),
            ),
        )

        viewModel.submitSignupWithCredentials()
        advanceUntilIdle()

        assertIs<SignupGeneralError.InvalidPassword>(viewModel.state.value.generalError)
    }

    @Test
    fun `RemoteOperationFailed NetworkNotAvailable emits ShowSnackbar NetworkUnavailable`() =
        runTest {
            val viewModel = createViewModel(
                signupWithCredentialsResult = Failure(
                    SignupRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.NetworkNotAvailable,
                    ),
                ),
            )

            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.effects.test {
                viewModel.submitSignupWithCredentials()
                advanceUntilIdle()
                val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
                assertIs<SignupSnackbarMessage.NetworkUnavailable>(effect.message)
            }
        }

    @Test
    fun `RemoteOperationFailed ServiceDown emits ShowSnackbar ServiceUnavailable`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.RemoteOperationFailed(RemoteError.Failed.ServiceDown),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithCredentials()
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            assertIs<SignupSnackbarMessage.ServiceUnavailable>(effect.message)
        }
    }

    @Test
    fun `RemoteOperationFailed Cooldown emits ShowSnackbar TooManyAttempts`() = runTest {
        val remaining = 5.minutes
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.RemoteOperationFailed(RemoteError.Failed.Cooldown(remaining)),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithCredentials()
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            val message = assertIs<SignupSnackbarMessage.TooManyAttempts>(effect.message)
            assertTrue(message.remaining == remaining)
        }
    }

    @Test
    fun `LocalOperationFailed StorageFull sets blockingError StorageFull`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )

        viewModel.submitSignupWithCredentials()
        advanceUntilIdle()

        assertIs<SignupBlockingError.StorageFull>(viewModel.state.value.blockingError)
    }

    @Test
    fun `LocalOperationFailed Corrupted sets blockingError StorageCorrupted`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.LocalOperationFailed(LocalStorageError.Corrupted),
            ),
        )

        viewModel.submitSignupWithCredentials()
        advanceUntilIdle()

        assertIs<SignupBlockingError.StorageCorrupted>(viewModel.state.value.blockingError)
    }

    @Test
    fun `LocalOperationFailed ReadOnly sets blockingError StorageReadOnly`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.LocalOperationFailed(LocalStorageError.ReadOnly),
            ),
        )

        viewModel.submitSignupWithCredentials()
        advanceUntilIdle()

        assertIs<SignupBlockingError.StorageReadOnly>(viewModel.state.value.blockingError)
    }

    @Test
    fun `LocalOperationFailed AccessDenied sets blockingError StorageAccessDenied`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.LocalOperationFailed(LocalStorageError.AccessDenied),
            ),
        )

        viewModel.submitSignupWithCredentials()
        advanceUntilIdle()

        assertIs<SignupBlockingError.StorageAccessDenied>(viewModel.state.value.blockingError)
    }

    @Test
    fun `LocalOperationFailed TemporarilyUnavailable emits ShowSnackbar`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.LocalOperationFailed(
                    LocalStorageError.TemporarilyUnavailable,
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithCredentials()
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            assertIs<SignupSnackbarMessage.StorageTemporarilyUnavailable>(effect.message)
        }
    }

    @Test
    fun `LocalOperationFailed UnknownError emits ShowSnackbar`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.LocalOperationFailed(
                    LocalStorageError.UnknownError(RuntimeException("test")),
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithCredentials()
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            assertIs<SignupSnackbarMessage.StorageTemporarilyUnavailable>(effect.message)
        }
    }

    @Test
    fun `isLoading is true while credentials signup is in progress`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.submitSignupWithCredentials()
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `double submit is prevented while loading`() = runTest {
        val deferred = CompletableDeferred<Unit>()
        val viewModel = SignupViewModel(
            signupWithGoogle = { _, _ -> error("Not used") },
            signupWithCredentials = { _, _ ->
                deferred.await()
                ResultWithError.Success(Unit)
            },
            savedStateHandle = SavedStateHandle(),
            profileNameValidator = ProfileNameValidatorImpl(),
            credentialsValidator = CredentialsValidatorImpl(),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithCredentials()
            runCurrent()
            assertTrue(viewModel.state.value.isLoading)
            viewModel.submitSignupWithCredentials()
            deferred.complete(Unit)
            advanceUntilIdle()
            assertIs<SignupSideEffects.NavigateToChatList>(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `retryLastAction after credentials signup clears blockingError and retries`() = runTest {
        val viewModel = createViewModel(
            signupWithCredentialsResult = Failure(
                SignupRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )

        viewModel.submitSignupWithCredentials()
        advanceUntilIdle()
        assertIs<SignupBlockingError.StorageFull>(viewModel.state.value.blockingError)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.retryLastAction()
            advanceUntilIdle()
            assertNull(viewModel.state.value.blockingError)
            assertIs<SignupSideEffects.NavigateToChatList>(awaitItem())
        }
    }

    @Test
    fun `retryLastAction after Google signup still works`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )

        viewModel.submitSignupWithGoogle("test-google-id-token")
        advanceUntilIdle()
        assertIs<SignupBlockingError.StorageFull>(viewModel.state.value.blockingError)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.retryLastAction()
            advanceUntilIdle()
            assertNull(viewModel.state.value.blockingError)
            assertIs<SignupSideEffects.NavigateToChatList>(awaitItem())
        }
    }
}
