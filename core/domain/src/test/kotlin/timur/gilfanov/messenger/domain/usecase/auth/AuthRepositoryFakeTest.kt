package timur.gilfanov.messenger.domain.usecase.auth

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleLoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(Unit::class)
class AuthRepositoryFakeTest {

    @Test
    fun `loginWithCredentials updates authState`() = runTest {
        val repository = AuthRepositoryFake()
        val credentials = Credentials(email = Email("a@b.com"), password = Password("pass"))

        repository.authState.test {
            assertEquals(AuthState.Unauthenticated, awaitItem())

            val result = repository.loginWithCredentials(credentials)
            assertIs<ResultWithError.Success<AuthSession, LoginRepositoryError>>(result)
            assertEquals(AuthProvider.EMAIL, result.data.provider)
            assertIs<AuthState.Authenticated>(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loginWithCredentials failure keeps authState unauthenticated`() = runTest {
        val repository = AuthRepositoryFake().apply {
            defaultLoginWithCredentialsResult =
                ResultWithError.Failure(LoginRepositoryError.InvalidCredentials)
        }
        val credentials = Credentials(email = Email("a@b.com"), password = Password("pass"))

        val result = repository.loginWithCredentials(credentials)
        assertIs<ResultWithError.Failure<AuthSession, LoginRepositoryError>>(result)
        assertEquals(AuthState.Unauthenticated, repository.currentAuthState)
    }

    @Test
    fun `loginWithGoogle can be configured and updates authState`() = runTest {
        val session = AuthSession(
            tokens = AuthTokens(accessToken = "tokenA", refreshToken = "tokenR"),
            provider = AuthProvider.GOOGLE,
        )
        val repository = AuthRepositoryFake().apply {
            defaultLoginWithGoogleResult = ResultWithError.Success(session)
        }

        val result = repository.loginWithGoogle(GoogleIdToken("id-token"))
        assertIs<ResultWithError.Success<AuthSession, GoogleLoginRepositoryError>>(result)
        assertEquals(AuthState.Authenticated(session), repository.currentAuthState)
    }

    @Test
    fun `refreshToken updates tokens for authenticated session`() = runTest {
        val initialSession = AuthSession(
            tokens = AuthTokens(accessToken = "oldA", refreshToken = "oldR"),
            provider = AuthProvider.EMAIL,
        )
        val repository = AuthRepositoryFake(initialSession)

        val refresh = repository.refreshToken()
        assertIs<ResultWithError.Success<AuthTokens, *>>(refresh)

        val state = repository.currentAuthState
        assertIs<AuthState.Authenticated>(state)
        assertEquals(refresh.data, state.session.tokens)
    }

    @Test
    fun `signup updates authState on success`() = runTest {
        val session = AuthSession(
            tokens = AuthTokens(accessToken = "access", refreshToken = "refresh"),
            provider = AuthProvider.EMAIL,
        )
        val repository = AuthRepositoryFake().apply {
            defaultSignupResult = ResultWithError.Success(session)
        }
        val credentials = Credentials(email = Email("a@b.com"), password = Password("pass"))

        val result = repository.signup(credentials = credentials, name = "Tim")
        assertIs<ResultWithError.Success<AuthSession, SignupRepositoryError>>(result)
        assertEquals(AuthState.Authenticated(session), repository.currentAuthState)
    }

    @Test
    fun `logout transitions to unauthenticated on success`() = runTest {
        val initialSession = AuthSession(
            tokens = AuthTokens(accessToken = "a1", refreshToken = "r1"),
            provider = AuthProvider.EMAIL,
        )
        val repository = AuthRepositoryFake(initialSession)

        val result = repository.logout()
        assertIs<ResultWithError.Success<Unit, LogoutRepositoryError>>(result)
        assertEquals(AuthState.Unauthenticated, repository.currentAuthState)
    }

    @Test
    fun `logout failure still transitions to unauthenticated`() = runTest {
        val initialSession = AuthSession(
            tokens = AuthTokens(accessToken = "a1", refreshToken = "r1"),
            provider = AuthProvider.EMAIL,
        )
        val repository = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult =
                ResultWithError.Failure(
                    LogoutRepositoryError.RemoteOperationFailed(RemoteError.Unauthenticated),
                )
        }

        val result = repository.logout()
        assertIs<ResultWithError.Failure<Unit, LogoutRepositoryError>>(result)
        assertEquals(AuthState.Unauthenticated, repository.currentAuthState)
    }

    @Test
    fun `logout local failure preserves authenticated state`() = runTest {
        val initialSession = AuthSession(
            tokens = AuthTokens(accessToken = "a1", refreshToken = "r1"),
            provider = AuthProvider.EMAIL,
        )
        val repository = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult = ResultWithError.Failure(
                LogoutRepositoryError.LocalOperationFailed(
                    LocalStorageError.TemporarilyUnavailable,
                ),
            )
        }

        val result = repository.logout()
        assertIs<ResultWithError.Failure<Unit, LogoutRepositoryError>>(result)
        assertEquals(AuthState.Authenticated(initialSession), repository.currentAuthState)
    }

    @Test
    fun `enqueueLoginWithCredentialsResult consumes results in order then falls back to default`() =
        runTest {
            val session1 = AuthSession(
                tokens = AuthTokens(accessToken = "a1", refreshToken = "r1"),
                provider = AuthProvider.EMAIL,
            )
            val session2 = AuthSession(
                tokens = AuthTokens(accessToken = "a2", refreshToken = "r2"),
                provider = AuthProvider.EMAIL,
            )
            val repository = AuthRepositoryFake().apply {
                defaultLoginWithCredentialsResult =
                    ResultWithError.Failure(LoginRepositoryError.InvalidCredentials)
                enqueueLoginWithCredentialsResult(
                    ResultWithError.Success(session1),
                    ResultWithError.Success(session2),
                )
            }
            val credentials = Credentials(email = Email("a@b.com"), password = Password("pass"))

            val first = repository.loginWithCredentials(credentials)
            assertIs<ResultWithError.Success<AuthSession, LoginRepositoryError>>(first)
            assertEquals(session1, first.data)
            assertEquals(AuthState.Authenticated(session1), repository.currentAuthState)

            val second = repository.loginWithCredentials(credentials)
            assertIs<ResultWithError.Success<AuthSession, LoginRepositoryError>>(second)
            assertEquals(session2, second.data)
            assertEquals(AuthState.Authenticated(session2), repository.currentAuthState)

            val third = repository.loginWithCredentials(credentials)
            assertIs<ResultWithError.Failure<AuthSession, LoginRepositoryError>>(third)
            assertEquals(AuthState.Authenticated(session2), repository.currentAuthState)
        }

    @Test
    fun `enqueueLoginWithGoogleResult consumes results in order then falls back to default`() =
        runTest {
            val session1 = AuthSession(
                tokens = AuthTokens(accessToken = "ga1", refreshToken = "gr1"),
                provider = AuthProvider.GOOGLE,
            )
            val session2 = AuthSession(
                tokens = AuthTokens(accessToken = "ga2", refreshToken = "gr2"),
                provider = AuthProvider.GOOGLE,
            )
            val repository = AuthRepositoryFake().apply {
                defaultLoginWithGoogleResult =
                    ResultWithError.Failure(GoogleLoginRepositoryError.InvalidToken)
                enqueueLoginWithGoogleResult(
                    ResultWithError.Success(session1),
                    ResultWithError.Success(session2),
                )
            }

            val first = repository.loginWithGoogle(GoogleIdToken("id-token"))
            assertIs<ResultWithError.Success<AuthSession, GoogleLoginRepositoryError>>(first)
            assertEquals(session1, first.data)
            assertEquals(AuthState.Authenticated(session1), repository.currentAuthState)

            val second = repository.loginWithGoogle(GoogleIdToken("id-token"))
            assertIs<ResultWithError.Success<AuthSession, GoogleLoginRepositoryError>>(second)
            assertEquals(session2, second.data)
            assertEquals(AuthState.Authenticated(session2), repository.currentAuthState)

            val third = repository.loginWithGoogle(GoogleIdToken("id-token"))
            assertIs<ResultWithError.Failure<AuthSession, GoogleLoginRepositoryError>>(third)
            assertEquals(AuthState.Authenticated(session2), repository.currentAuthState)
        }

    @Test
    fun `enqueueRefreshTokenResult consumes results in order then falls back to default`() =
        runTest {
            val initialSession = AuthSession(
                tokens = AuthTokens(accessToken = "a1", refreshToken = "r1"),
                provider = AuthProvider.EMAIL,
            )
            val newTokens = AuthTokens(accessToken = "a2", refreshToken = "r2")
            val repository = AuthRepositoryFake(initialSession).apply {
                defaultRefreshTokenResult =
                    ResultWithError.Failure(RefreshRepositoryError.TokenExpired)
                enqueueRefreshTokenResult(
                    ResultWithError.Success(newTokens),
                    ResultWithError.Failure(RefreshRepositoryError.SessionRevoked),
                )
            }

            val first = repository.refreshToken()
            assertIs<ResultWithError.Success<AuthTokens, RefreshRepositoryError>>(first)
            assertEquals(newTokens, first.data)
            assertEquals(
                AuthState.Authenticated(initialSession.copy(tokens = newTokens)),
                repository.currentAuthState,
            )

            val second = repository.refreshToken()
            assertIs<ResultWithError.Failure<AuthTokens, RefreshRepositoryError>>(second)
            assertEquals(RefreshRepositoryError.SessionRevoked, second.error)
            assertEquals(
                AuthState.Authenticated(initialSession.copy(tokens = newTokens)),
                repository.currentAuthState,
            )

            val third = repository.refreshToken()
            assertIs<ResultWithError.Failure<AuthTokens, RefreshRepositoryError>>(third)
            assertEquals(RefreshRepositoryError.TokenExpired, third.error)
            assertEquals(
                AuthState.Authenticated(initialSession.copy(tokens = newTokens)),
                repository.currentAuthState,
            )
        }

    @Test
    fun `refreshToken unauthenticated returns SessionRevoked and keeps state unauthenticated`() =
        runTest {
            val repository = AuthRepositoryFake()

            val result = repository.refreshToken()
            assertIs<ResultWithError.Failure<AuthTokens, RefreshRepositoryError>>(result)
            assertEquals(RefreshRepositoryError.SessionRevoked, result.error)
            assertEquals(AuthState.Unauthenticated, repository.currentAuthState)
        }
}
