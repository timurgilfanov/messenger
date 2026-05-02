package timur.gilfanov.messenger.auth

import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshError
import timur.gilfanov.messenger.auth.domain.usecase.TokenRefreshUseCaseImpl
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class TokenRefreshUseCaseTest {

    private val initialSession = AuthSession(
        tokens = AuthTokens(
            accessToken = "initial-access-token",
            refreshToken = "initial-refresh-token",
        ),
        provider = AuthProvider.EMAIL,
    )
    private val newTokens = AuthTokens(
        accessToken = "new-access-token",
        refreshToken = "new-refresh-token",
    )

    private fun createUseCase(
        authRepository: AuthRepositoryFake = AuthRepositoryFake(initialSession),
    ) = TokenRefreshUseCaseImpl(authRepository, NoOpLogger())

    @Test
    fun `when refresh succeeds then returns new tokens and state remains authenticated`() =
        runTest {
            val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
                defaultRefreshTokenResult = ResultWithError.Success(newTokens)
            }
            val useCase = createUseCase(authRepositoryFake)

            val result = useCase()

            assertIs<ResultWithError.Success<AuthTokens, *>>(result)
            assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
        }

    @Test
    fun `when TokenExpired then session becomes Unauthenticated and returns SessionExpired`() =
        runTest {
            val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
                defaultLogoutResult = ResultWithError.Success(Unit)
                defaultRefreshTokenResult =
                    ResultWithError.Failure(RefreshRepositoryError.TokenExpired)
            }

            val useCase = createUseCase(authRepositoryFake)

            val result = useCase()

            assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
            assertIs<TokenRefreshError.SessionExpired>(result.error)
            assertIs<AuthState.Unauthenticated>(authRepositoryFake.currentAuthState)
        }

    @Test
    fun `when SessionRevoked then session becomes Unauthenticated and returns SessionExpired`() =
        runTest {
            val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
                defaultLogoutResult = ResultWithError.Success(Unit)
                defaultRefreshTokenResult =
                    ResultWithError.Failure(RefreshRepositoryError.SessionRevoked)
            }
            val useCase = createUseCase(authRepositoryFake)

            val result = useCase()

            assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
            assertIs<TokenRefreshError.SessionExpired>(result.error)
            assertIs<AuthState.Unauthenticated>(authRepositoryFake.currentAuthState)
        }

    @Test
    fun `when LocalOperationFailed then returns LocalOperationFailed without logout`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultRefreshTokenResult = ResultWithError.Failure(
                RefreshRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            )
        }
        val useCase = createUseCase(authRepositoryFake)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.LocalOperationFailed>(result.error)
        assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
    }

    @Test
    fun `when RemoteOperationFailed then returns RemoteOperationFailed without logout`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultRefreshTokenResult = ResultWithError.Failure(
                RefreshRepositoryError.RemoteOperationFailed(RemoteError.Failed.ServiceDown),
            )
        }
        val useCase = createUseCase(authRepositoryFake)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.RemoteOperationFailed>(result.error)
        assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
    }

    @Test
    fun `when TokenExpired and local logout fails then returns LocalOperationFailed`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult = ResultWithError.Failure(
                LogoutRepositoryError.LocalOperationFailed(LocalStorageError.AccessDenied),
            )
            defaultRefreshTokenResult =
                ResultWithError.Failure(RefreshRepositoryError.TokenExpired)
        }
        val useCase = createUseCase(authRepositoryFake)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.LocalOperationFailed>(result.error)
        assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
    }

    @Test
    fun `when SessionRevoked and local logout fails then returns LocalOperationFailed`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult = ResultWithError.Failure(
                LogoutRepositoryError.LocalOperationFailed(LocalStorageError.AccessDenied),
            )
            defaultRefreshTokenResult =
                ResultWithError.Failure(RefreshRepositoryError.SessionRevoked)
        }
        val useCase = createUseCase(authRepositoryFake)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.LocalOperationFailed>(result.error)
        assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
    }

    @Test
    fun `when TokenExpired and remote logout fails then returns SessionExpired`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult = ResultWithError.Failure(
                LogoutRepositoryError.RemoteOperationFailed(RemoteError.Failed.ServiceDown),
            )
            defaultRefreshTokenResult =
                ResultWithError.Failure(RefreshRepositoryError.TokenExpired)
        }
        val useCase = createUseCase(authRepositoryFake)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.SessionExpired>(result.error)
        assertIs<AuthState.Unauthenticated>(authRepositoryFake.currentAuthState)
    }

    @Test
    fun `when SessionRevoked and remote logout fails then returns SessionExpired`() = runTest {
        val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
            defaultLogoutResult = ResultWithError.Failure(
                LogoutRepositoryError.RemoteOperationFailed(RemoteError.Failed.ServiceDown),
            )
            defaultRefreshTokenResult =
                ResultWithError.Failure(RefreshRepositoryError.SessionRevoked)
        }
        val useCase = createUseCase(authRepositoryFake)

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.SessionExpired>(result.error)
        assertIs<AuthState.Unauthenticated>(authRepositoryFake.currentAuthState)
    }
}
