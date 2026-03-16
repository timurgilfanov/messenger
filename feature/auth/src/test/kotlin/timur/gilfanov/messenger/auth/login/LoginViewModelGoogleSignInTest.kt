package timur.gilfanov.messenger.auth.login

import app.cash.turbine.test
import kotlin.test.assertIs
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
    fun `google sign in NetworkNotAvailable sets generalError NetworkUnavailable`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(
                GoogleLoginRepositoryError.RemoteOperationFailed(
                    RemoteError.Failed.NetworkNotAvailable,
                ),
            ),
        )
        viewModel.submitGoogleSignIn(testIdToken)
        advanceUntilIdle()
        assertIs<LoginGeneralError.NetworkUnavailable>(viewModel.state.value.generalError)
    }

    @Test
    fun `google sign in LocalOperationFailed sets generalError ServiceUnavailable`() = runTest {
        val viewModel = createViewModel(
            googleLoginResult = Failure(
                GoogleLoginRepositoryError.LocalOperationFailed(
                    LocalStorageError.TemporarilyUnavailable,
                ),
            ),
        )
        viewModel.submitGoogleSignIn(testIdToken)
        advanceUntilIdle()
        assertIs<LoginGeneralError.ServiceUnavailable>(viewModel.state.value.generalError)
    }

    @Test
    fun `isLoading is true while google sign in is in progress`() = runTest {
        val viewModel = createViewModel()

        viewModel.state.test {
            awaitItem()
            viewModel.submitGoogleSignIn(testIdToken)
            val loadingState = awaitItem()
            assert(loadingState.isLoading)
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
