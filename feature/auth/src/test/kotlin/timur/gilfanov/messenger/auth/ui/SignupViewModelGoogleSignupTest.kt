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
import timur.gilfanov.messenger.auth.domain.usecase.SignupWithGoogleUseCaseError
import timur.gilfanov.messenger.auth.ui.SignupViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupBlockingError
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupGeneralError
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupSideEffects
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupSnackbarMessage
import timur.gilfanov.messenger.auth.ui.screen.signup.SignupViewModel
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.common.ErrorReason
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class SignupViewModelGoogleSignupTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testIdToken = "test-google-id-token"

    @Test
    fun `google signup success emits NavigateToChatList`() = runTest {
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            assertIs<SignupSideEffects.NavigateToChatList>(awaitItem())
        }
    }

    @Test
    fun `google signup InvalidToken sets generalError InvalidToken`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(GoogleSignupRepositoryError.InvalidToken),
        )
        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()
        assertIs<SignupGeneralError.InvalidToken>(viewModel.state.value.generalError)
    }

    @Test
    fun `google signup AccountAlreadyExists sets generalError AccountAlreadyExists`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(GoogleSignupRepositoryError.AccountAlreadyExists),
        )
        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()
        assertIs<SignupGeneralError.AccountAlreadyExists>(viewModel.state.value.generalError)
    }

    @Test
    fun `google signup InvalidName server sets nameError`() = runTest {
        val validationError = ProfileNameValidationError.LengthOutOfBounds(
            length = 100,
            min = 1,
            max = 50,
        )
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.InvalidName(validationError),
            ),
        )
        viewModel.updateName("a".repeat(100))
        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()
        assertIs<ProfileNameValidationError.LengthOutOfBounds>(viewModel.state.value.nameError)
    }

    @Test
    fun `google signup NetworkNotAvailable emits ShowSnackbar NetworkUnavailable`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.RemoteOperationFailed(
                    RemoteError.Failed.NetworkNotAvailable,
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            assertIs<SignupSnackbarMessage.NetworkUnavailable>(effect.message)
        }
    }

    @Test
    fun `google signup ServiceDown emits ShowSnackbar ServiceUnavailable`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.RemoteOperationFailed(
                    RemoteError.Failed.ServiceDown,
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            assertIs<SignupSnackbarMessage.ServiceUnavailable>(effect.message)
        }
    }

    @Test
    fun `google signup UnknownServiceError emits ShowSnackbar ServiceUnavailable`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.RemoteOperationFailed(
                    RemoteError.Failed.UnknownServiceError(ErrorReason("test error")),
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            assertIs<SignupSnackbarMessage.ServiceUnavailable>(effect.message)
        }
    }

    @Test
    fun `google signup ServiceTimeout emits ShowSnackbar ServiceUnavailable`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.RemoteOperationFailed(
                    RemoteError.UnknownStatus.ServiceTimeout,
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            assertIs<SignupSnackbarMessage.ServiceUnavailable>(effect.message)
        }
    }

    @Test
    fun `google signup Cooldown emits ShowSnackbar TooManyAttempts with remaining duration`() =
        runTest {
            val remaining = 3.minutes
            val viewModel = createViewModel(
                signupWithGoogleResult = Failure(
                    GoogleSignupRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.Cooldown(remaining),
                    ),
                ),
            )

            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.effects.test {
                viewModel.submitSignupWithGoogle(testIdToken)
                advanceUntilIdle()
                val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
                val message = assertIs<SignupSnackbarMessage.TooManyAttempts>(effect.message)
                assertTrue(message.remaining == remaining)
            }
        }

    @Test
    fun `google signup local TemporarilyUnavailable emits ShowSnackbar`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.LocalOperationFailed(
                    LocalStorageError.TemporarilyUnavailable,
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            assertIs<SignupSnackbarMessage.StorageTemporarilyUnavailable>(effect.message)
        }
    }

    @Test
    fun `google signup local UnknownError emits ShowSnackbar`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.LocalOperationFailed(
                    LocalStorageError.UnknownError(RuntimeException("test")),
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            val effect = assertIs<SignupSideEffects.ShowSnackbar>(awaitItem())
            assertIs<SignupSnackbarMessage.StorageTemporarilyUnavailable>(effect.message)
        }
    }

    @Test
    fun `google signup LocalOperationFailed StorageFull sets blockingError StorageFull`() =
        runTest {
            val viewModel = createViewModel(
                signupWithGoogleResult = Failure(
                    GoogleSignupRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
                ),
            )
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            assertIs<SignupBlockingError.StorageFull>(viewModel.state.value.blockingError)
        }

    @Test
    fun `google signup LocalOperationFailed Corrupted sets blockingError Corrupted`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.LocalOperationFailed(LocalStorageError.Corrupted),
            ),
        )
        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()
        assertIs<SignupBlockingError.StorageCorrupted>(viewModel.state.value.blockingError)
    }

    @Test
    fun `google signup LocalOperationFailed ReadOnly sets blockingError ReadOnly`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.LocalOperationFailed(LocalStorageError.ReadOnly),
            ),
        )
        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()
        assertIs<SignupBlockingError.StorageReadOnly>(viewModel.state.value.blockingError)
    }

    @Test
    fun `google signup LocalOperationFailed AccessDenied sets blockingError AccessDenied`() =
        runTest {
            val viewModel = createViewModel(
                signupWithGoogleResult = Failure(
                    GoogleSignupRepositoryError.LocalOperationFailed(
                        LocalStorageError.AccessDenied,
                    ),
                ),
            )
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            assertIs<SignupBlockingError.StorageAccessDenied>(viewModel.state.value.blockingError)
        }

    @Test
    fun `isLoading is true while google signup is in progress`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.submitSignupWithGoogle(testIdToken)
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            advanceUntilIdle()
            val doneState = awaitItem()
            assertTrue(!doneState.isLoading)
        }
    }

    @Test
    fun `second submitSignupWithGoogle while first is in progress is ignored`() = runTest {
        val deferred = CompletableDeferred<Unit>()
        val viewModel = SignupViewModel(
            signupWithGoogle = { _, _ ->
                deferred.await()
                ResultWithError.Success(Unit)
            },
            signupWithCredentials = { _, _ -> error("Not used") },
            savedStateHandle = SavedStateHandle(),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitSignupWithGoogle(testIdToken)
            runCurrent()
            assertTrue(viewModel.state.value.isLoading)
            viewModel.submitSignupWithGoogle(testIdToken)
            deferred.complete(Unit)
            advanceUntilIdle()
            assertIs<SignupSideEffects.NavigateToChatList>(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `onOpenAppSettingsClick clears blockingError and emits OpenAppSettings`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.LocalOperationFailed(LocalStorageError.Corrupted),
            ),
        )
        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()
        assertIs<SignupBlockingError.StorageCorrupted>(viewModel.state.value.blockingError)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.onOpenAppSettingsClick()
            assertIs<SignupSideEffects.OpenAppSettings>(awaitItem())
            assertNull(viewModel.state.value.blockingError)
        }
    }

    @Test
    fun `onOpenStorageSettingsClick clears blockingError and emits OpenStorageSettings`() =
        runTest {
            val viewModel = createViewModel(
                signupWithGoogleResult = Failure(
                    GoogleSignupRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
                ),
            )
            viewModel.submitSignupWithGoogle(testIdToken)
            advanceUntilIdle()
            assertIs<SignupBlockingError.StorageFull>(viewModel.state.value.blockingError)

            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.effects.test {
                viewModel.onOpenStorageSettingsClick()
                assertIs<SignupSideEffects.OpenStorageSettings>(awaitItem())
                assertNull(viewModel.state.value.blockingError)
            }
        }

    @Test
    fun `retryLastAction after google signup clears blockingError and retries`() = runTest {
        val viewModel = createViewModel(
            signupWithGoogleResult = Failure(
                GoogleSignupRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )
        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()
        assertIs<SignupBlockingError.StorageFull>(viewModel.state.value.blockingError)

        viewModel.retryLastAction()
        advanceUntilIdle()
        assertNull(viewModel.state.value.blockingError)
    }

    @Test
    fun `retryLastAction success emits NavigateToChatList`() = runTest {
        var callCount = 0
        val viewModel = SignupViewModel(
            signupWithGoogle = { _, _ ->
                callCount++
                if (callCount == 1) {
                    Failure(
                        SignupWithGoogleUseCaseError.LocalOperationFailed(
                            LocalStorageError.StorageFull,
                        ),
                    )
                } else {
                    ResultWithError.Success(Unit)
                }
            },
            signupWithCredentials = { _, _ -> error("Not used") },
            savedStateHandle = SavedStateHandle(),
        )
        viewModel.submitSignupWithGoogle(testIdToken)
        advanceUntilIdle()
        assertIs<SignupBlockingError.StorageFull>(viewModel.state.value.blockingError)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.retryLastAction()
            advanceUntilIdle()
            assertIs<SignupSideEffects.NavigateToChatList>(awaitItem())
        }
    }
}
