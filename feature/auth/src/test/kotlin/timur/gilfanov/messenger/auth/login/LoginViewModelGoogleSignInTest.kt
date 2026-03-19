package timur.gilfanov.messenger.auth.login

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
import timur.gilfanov.messenger.auth.login.LoginViewModelTestFixtures.createViewModel
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleLoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class LoginViewModelGoogleSignInTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testIdToken = "test-google-id-token"

    @Test
    fun `google sign in success emits NavigateToChatList`() = runTest {
        val viewModel = createViewModel()

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitGoogleSignIn(testIdToken)
            advanceUntilIdle()
            assertIs<LoginSideEffects.NavigateToChatList>(awaitItem())
        }
    }

    @Test
    fun `google sign in InvalidToken sets generalError InvalidToken`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(GoogleLoginRepositoryError.InvalidToken),
        )
        viewModel.submitGoogleSignIn(testIdToken)
        advanceUntilIdle()
        assertIs<LoginGeneralError.InvalidToken>(viewModel.state.value.generalError)
    }

    @Test
    fun `google sign in AccountNotFound sets generalError AccountNotFound`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(GoogleLoginRepositoryError.AccountNotFound),
        )
        viewModel.submitGoogleSignIn(testIdToken)
        advanceUntilIdle()
        assertIs<LoginGeneralError.AccountNotFound>(viewModel.state.value.generalError)
    }

    @Test
    fun `google sign in AccountSuspended sets generalError AccountSuspended`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(GoogleLoginRepositoryError.AccountSuspended),
        )
        viewModel.submitGoogleSignIn(testIdToken)
        advanceUntilIdle()
        assertIs<LoginGeneralError.AccountSuspended>(viewModel.state.value.generalError)
    }

    @Test
    fun `google sign in NetworkNotAvailable emits ShowSnackbar NetworkUnavailable`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(
                GoogleLoginRepositoryError.RemoteOperationFailed(
                    RemoteError.Failed.NetworkNotAvailable,
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitGoogleSignIn(testIdToken)
            advanceUntilIdle()
            val effect = assertIs<LoginSideEffects.ShowSnackbar>(awaitItem())
            assertIs<LoginSnackbarMessage.NetworkUnavailable>(effect.message)
        }
    }

    @Test
    fun `google sign in local TemporarilyUnavailable emits ShowSnackbar`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(
                GoogleLoginRepositoryError.LocalOperationFailed(
                    LocalStorageError.TemporarilyUnavailable,
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitGoogleSignIn(testIdToken)
            advanceUntilIdle()
            val effect = assertIs<LoginSideEffects.ShowSnackbar>(awaitItem())
            assertIs<LoginSnackbarMessage.StorageTemporarilyUnavailable>(effect.message)
        }
    }

    @Test
    fun `google sign in LocalOperationFailed StorageFull sets blockingError StorageFull`() =
        runTest {
            val viewModel = createViewModel(
                googleLoginResult = Failure(
                    GoogleLoginRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
                ),
            )
            viewModel.submitGoogleSignIn(testIdToken)
            advanceUntilIdle()
            assertIs<LoginBlockingError.StorageFull>(viewModel.state.value.blockingError)
        }

    @Test
    fun `google sign in LocalOperationFailed Corrupted sets blockingError Corrupted`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(
                GoogleLoginRepositoryError.LocalOperationFailed(LocalStorageError.Corrupted),
            ),
        )
        viewModel.submitGoogleSignIn(testIdToken)
        advanceUntilIdle()
        assertIs<LoginBlockingError.StorageCorrupted>(viewModel.state.value.blockingError)
    }

    @Test
    fun `google sign in LocalOperationFailed ReadOnly sets blockingError ReadOnly`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(
                GoogleLoginRepositoryError.LocalOperationFailed(LocalStorageError.ReadOnly),
            ),
        )
        viewModel.submitGoogleSignIn(testIdToken)
        advanceUntilIdle()
        assertIs<LoginBlockingError.StorageReadOnly>(viewModel.state.value.blockingError)
    }

    @Test
    fun `google sign in LocalOperationFailed AccessDenied sets blockingError AccessDenied`() =
        runTest {
            val viewModel = createViewModel(
                googleLoginResult = Failure(
                    GoogleLoginRepositoryError.LocalOperationFailed(LocalStorageError.AccessDenied),
                ),
            )
            viewModel.submitGoogleSignIn(testIdToken)
            advanceUntilIdle()
            assertIs<LoginBlockingError.StorageAccessDenied>(viewModel.state.value.blockingError)
        }

    @Test
    fun `google sign in local UnknownError emits ShowSnackbar`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(
                GoogleLoginRepositoryError.LocalOperationFailed(
                    LocalStorageError.UnknownError(RuntimeException("test")),
                ),
            ),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitGoogleSignIn(testIdToken)
            advanceUntilIdle()
            val effect = assertIs<LoginSideEffects.ShowSnackbar>(awaitItem())
            assertIs<LoginSnackbarMessage.StorageTemporarilyUnavailable>(effect.message)
        }
    }

    @Test
    fun `google sign in Cooldown emits ShowSnackbar TooManyAttempts with remaining duration`() =
        runTest {
            val remaining = 3.minutes
            val viewModel = createViewModel(
                googleLoginResult = Failure(
                    GoogleLoginRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.Cooldown(remaining),
                    ),
                ),
            )

            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.effects.test {
                viewModel.submitGoogleSignIn(testIdToken)
                advanceUntilIdle()
                val effect = assertIs<LoginSideEffects.ShowSnackbar>(awaitItem())
                val message = assertIs<LoginSnackbarMessage.TooManyAttempts>(effect.message)
                assertTrue(message.remaining == remaining)
            }
        }

    @Test
    fun `onOpenAppSettingsClick clears blockingError and emits OpenAppSettings`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(
                GoogleLoginRepositoryError.LocalOperationFailed(LocalStorageError.Corrupted),
            ),
        )
        viewModel.submitGoogleSignIn(testIdToken)
        advanceUntilIdle()
        assertIs<LoginBlockingError.StorageCorrupted>(viewModel.state.value.blockingError)

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.onOpenAppSettingsClick()
            assertIs<LoginSideEffects.OpenAppSettings>(awaitItem())
            assertNull(viewModel.state.value.blockingError)
        }
    }

    @Test
    fun `onOpenStorageSettingsClick clears blockingError and emits OpenStorageSettings`() =
        runTest {
            val viewModel = createViewModel(
                googleLoginResult = Failure(
                    GoogleLoginRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
                ),
            )
            viewModel.submitGoogleSignIn(testIdToken)
            advanceUntilIdle()
            assertIs<LoginBlockingError.StorageFull>(viewModel.state.value.blockingError)

            backgroundScope.launch { viewModel.state.collect {} }
            viewModel.effects.test {
                viewModel.onOpenStorageSettingsClick()
                assertIs<LoginSideEffects.OpenStorageSettings>(awaitItem())
                assertNull(viewModel.state.value.blockingError)
            }
        }

    @Test
    fun `retryLastAction after google sign in clears blockingError and retries`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(
                GoogleLoginRepositoryError.LocalOperationFailed(
                    LocalStorageError.StorageFull,
                ),
            ),
        )
        viewModel.submitGoogleSignIn(testIdToken)
        advanceUntilIdle()
        assertIs<LoginBlockingError.StorageFull>(viewModel.state.value.blockingError)

        viewModel.retryLastAction()
        advanceUntilIdle()
        assertNull(viewModel.state.value.blockingError)
    }

    @Test
    fun `isLoading is true while google sign in is in progress`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.submitGoogleSignIn(testIdToken)
            val loadingState = awaitItem()
            assertTrue(loadingState.isLoading)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `second submitGoogleSignIn while first is in progress is ignored`() = runTest {
        val deferred = CompletableDeferred<Unit>()
        val viewModel = LoginViewModel(
            loginWithCredentials = { ResultWithError.Success(Unit) },
            loginWithGoogle = {
                deferred.await()
                ResultWithError.Success(Unit)
            },
            savedStateHandle = SavedStateHandle(),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.effects.test {
            viewModel.submitGoogleSignIn(testIdToken)
            runCurrent()
            assertTrue(viewModel.state.value.isLoading)
            viewModel.submitGoogleSignIn(testIdToken)
            deferred.complete(Unit)
            advanceUntilIdle()
            assertIs<LoginSideEffects.NavigateToChatList>(awaitItem())
            expectNoEvents()
        }
    }
}
