package timur.gilfanov.messenger.ui.activity

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleLoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule

private class NeverEmitsAuthRepository : AuthRepository {
    override val authState: Flow<AuthState> = flow { suspendCancellableCoroutine { } }

    override suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthSession, LoginRepositoryError> = error("not expected")

    override suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthSession, GoogleLoginRepositoryError> = error("not expected")

    override suspend fun signupWithGoogle(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<AuthSession, GoogleSignupRepositoryError> = error("not expected")

    override suspend fun signup(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthSession, SignupRepositoryError> = error("not expected")

    override suspend fun logout(): ResultWithError<Unit, LogoutRepositoryError> =
        error("not expected")

    override suspend fun refreshToken(): ResultWithError<AuthTokens, RefreshRepositoryError> =
        error("not expected")
}

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class MainActivityViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testSession = AuthSession(
        tokens = AuthTokens(
            accessToken = "test-access-token",
            refreshToken = "test-refresh-token",
        ),
        provider = AuthProvider.EMAIL,
    )

    private fun createViewModel(authRepository: AuthRepository): MainActivityViewModel =
        MainActivityViewModel(
            observeAndApplyLocale = { emptyFlow() },
            authRepository = authRepository,
            logger = NoOpLogger(),
        )

    @Test
    fun `no effect is emitted before auth state resolves`() = runTest {
        val viewModel = createViewModel(authRepository = NeverEmitsAuthRepository())

        viewModel.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `authenticated initial auth state emits Authenticated effect`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))

        val viewModel = createViewModel(authRepository = authRepository)

        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(MainActivitySideEffect.Authenticated, awaitItem())
        }
    }

    @Test
    fun `unauthenticated initial auth state emits Unauthenticated effect`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)

        val viewModel = createViewModel(authRepository = authRepository)

        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(MainActivitySideEffect.Unauthenticated, awaitItem())
        }
    }

    @Test
    fun `authenticated runtime session expiry emits Unauthenticated effect`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val viewModel = createViewModel(authRepository = authRepository)

        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(MainActivitySideEffect.Authenticated, awaitItem())

            authRepository.setState(AuthState.Unauthenticated)
            advanceUntilIdle()
            assertEquals(MainActivitySideEffect.Unauthenticated, awaitItem())
        }
    }

    @Test
    fun `unauthenticated to authenticated transition emits Authenticated effect`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)
        val viewModel = createViewModel(authRepository = authRepository)

        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(MainActivitySideEffect.Unauthenticated, awaitItem())

            authRepository.setState(AuthState.Authenticated(testSession))
            advanceUntilIdle()
            assertEquals(MainActivitySideEffect.Authenticated, awaitItem())
        }
    }
}
