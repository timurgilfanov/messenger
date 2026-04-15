package timur.gilfanov.messenger.ui.activity

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import timur.gilfanov.messenger.domain.usecase.settings.LocaleRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.ObserveAndApplyLocaleUseCase
import timur.gilfanov.messenger.domain.usecase.settings.ObserveUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryStub
import timur.gilfanov.messenger.navigation.Login
import timur.gilfanov.messenger.navigation.Main
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

    private fun createNoOpLocaleUseCase(
        localeAuthRepo: AuthRepository,
    ): ObserveAndApplyLocaleUseCase {
        val logger = NoOpLogger()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            authRepository = localeAuthRepo,
            settingsRepository = SettingsRepositoryStub(),
            logger = logger,
        )
        return ObserveAndApplyLocaleUseCase(
            observeUiLanguage = observeUiLanguage,
            localeRepository = LocaleRepositoryStub(),
            logger = logger,
        )
    }

    private fun createViewModel(
        authRepository: AuthRepository,
        localeAuthRepository: AuthRepository = AuthRepositoryFake(AuthState.Unauthenticated),
    ): MainActivityViewModel = MainActivityViewModel(
        observeAndApplyLocale = createNoOpLocaleUseCase(localeAuthRepository),
        authRepository = authRepository,
        logger = NoOpLogger(),
    )

    @Test
    fun `ui state is Loading before auth state emits`() = runTest {
        val viewModel = createViewModel(authRepository = NeverEmitsAuthRepository())

        assertEquals(MainActivityUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `authenticated auth state resolves destination to Main`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))

        val viewModel = createViewModel(authRepository = authRepository)

        advanceUntilIdle()

        assertEquals(
            MainActivityUiState.Ready(initialDestination = Main),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `unauthenticated auth state resolves destination to Login`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)

        val viewModel = createViewModel(authRepository = authRepository)

        advanceUntilIdle()

        assertEquals(
            MainActivityUiState.Ready(initialDestination = Login),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `authenticated runtime session expiry emits NavigateToLogin effect`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val viewModel = createViewModel(authRepository = authRepository)
        advanceUntilIdle()

        viewModel.effects.test {
            authRepository.setState(AuthState.Unauthenticated)
            advanceUntilIdle()

            assertEquals(MainActivitySideEffect.NavigateToLogin, awaitItem())
        }
    }

    @Test
    fun `initial Unauthenticated state does not emit NavigateToLogin effect`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)
        val viewModel = createViewModel(authRepository = authRepository)

        viewModel.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `initial Authenticated state does not emit NavigateToLogin effect`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val viewModel = createViewModel(authRepository = authRepository)

        viewModel.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }
    }
}
