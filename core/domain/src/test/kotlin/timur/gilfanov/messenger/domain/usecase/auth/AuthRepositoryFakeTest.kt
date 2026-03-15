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
}
