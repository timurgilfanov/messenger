package timur.gilfanov.messenger.auth

import java.util.UUID
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
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
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
        userId = UserId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
    )
    private val newTokens = AuthTokens(
        accessToken = "new-access-token",
        refreshToken = "new-refresh-token",
    )

    @Test
    fun `when refresh succeeds then returns new tokens and state remains authenticated`() =
        runTest {
            val authRepositoryFake = AuthRepositoryFake(initialSession).apply {
                defaultRefreshTokenResult = ResultWithError.Success(newTokens)
            }
            val useCase = TokenRefreshUseCaseImpl(authRepositoryFake, NoOpLogger())

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

            val useCase = TokenRefreshUseCaseImpl(authRepositoryFake, NoOpLogger())

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
            val useCase = TokenRefreshUseCaseImpl(authRepositoryFake, NoOpLogger())

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
        val useCase = TokenRefreshUseCaseImpl(authRepositoryFake, NoOpLogger())

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
        val useCase = TokenRefreshUseCaseImpl(authRepositoryFake, NoOpLogger())

        val result = useCase()

        assertIs<ResultWithError.Failure<AuthTokens, TokenRefreshError>>(result)
        assertIs<TokenRefreshError.RemoteOperationFailed>(result.error)
        assertIs<AuthState.Authenticated>(authRepositoryFake.currentAuthState)
    }
}
