package timur.gilfanov.messenger.auth.data.repository

import app.cash.turbine.test
import kotlin.test.assertIs
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.data.source.remote.LoginWithCredentialsError
import timur.gilfanov.messenger.auth.data.source.remote.LogoutError
import timur.gilfanov.messenger.auth.data.source.remote.RefreshError
import timur.gilfanov.messenger.auth.data.source.remote.RegisterError
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSourceFake
import timur.gilfanov.messenger.auth.data.storage.AuthSessionStorageError
import timur.gilfanov.messenger.auth.data.storage.AuthSessionStorageFake
import timur.gilfanov.messenger.data.remote.RemoteDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.auth.Credentials
import timur.gilfanov.messenger.domain.entity.auth.Email
import timur.gilfanov.messenger.domain.entity.auth.GoogleIdToken
import timur.gilfanov.messenger.domain.entity.auth.Password
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class AuthRepositoryImplTest {

    private val credentials = Credentials(Email("test@example.com"), Password("password123"))
    private val googleIdToken = GoogleIdToken("google-id-token")

    private fun createRepo(
        remoteDataSource: RemoteAuthDataSourceFake = RemoteAuthDataSourceFake(),
        sessionStorage: AuthSessionStorageFake = AuthSessionStorageFake(),
        scope: kotlinx.coroutines.CoroutineScope,
    ) = AuthRepositoryImpl(
        remoteDataSource = remoteDataSource,
        sessionStorage = sessionStorage,
        coroutineScope = scope,
        logger = NoOpLogger(),
    )

    @Test
    fun `loginWithCredentials success stores session and sets Authenticated EMAIL provider`() =
        runTest {
            val storage = AuthSessionStorageFake()
            val repo = createRepo(sessionStorage = storage, scope = this)
            advanceUntilIdle()

            val result = repo.loginWithCredentials(credentials)

            assertIs<ResultWithError.Success<AuthSession, LoginRepositoryError>>(result)
            kotlin.test.assertEquals(AuthProvider.EMAIL, result.data.provider)
            repo.authState.test {
                assertIs<AuthState.Authenticated>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loginWithCredentials InvalidCredentials returns error and storage not called`() = runTest {
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueLoginWithCredentials(
            ResultWithError.Failure(LoginWithCredentialsError.InvalidCredentials),
        )
        val storage = AuthSessionStorageFake()
        val repo = createRepo(remoteDataSource = remote, sessionStorage = storage, scope = this)
        advanceUntilIdle()

        val result = repo.loginWithCredentials(credentials)

        val failure = assertIs<ResultWithError.Failure<AuthSession, LoginRepositoryError>>(result)
        assertIs<LoginRepositoryError.InvalidCredentials>(failure.error)
    }

    @Test
    fun `loginWithCredentials storage failure returns LocalOperationFailed`() = runTest {
        val storage = AuthSessionStorageFake()
        storage.enqueueSaveSession(
            ResultWithError.Failure(AuthSessionStorageError.AccessDenied),
        )
        val repo = createRepo(sessionStorage = storage, scope = this)
        advanceUntilIdle()

        val result = repo.loginWithCredentials(credentials)

        val failure = assertIs<ResultWithError.Failure<AuthSession, LoginRepositoryError>>(result)
        assertIs<LoginRepositoryError.LocalOperationFailed>(failure.error)
    }

    @Test
    fun `loginWithGoogle success stores session and sets Authenticated GOOGLE provider`() =
        runTest {
            val storage = AuthSessionStorageFake()
            val repo = createRepo(sessionStorage = storage, scope = this)
            advanceUntilIdle()

            val result = repo.loginWithGoogle(googleIdToken)

            assertIs<ResultWithError.Success<AuthSession, *>>(result)
            kotlin.test.assertEquals(AuthProvider.GOOGLE, result.data.provider)
            repo.authState.test {
                assertIs<AuthState.Authenticated>(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `signup success session saved and authState is Authenticated`() = runTest {
        val storage = AuthSessionStorageFake()
        val repo = createRepo(sessionStorage = storage, scope = this)
        advanceUntilIdle()

        val result = repo.signup(credentials, "Test User")

        assertIs<ResultWithError.Success<AuthSession, *>>(result)
        repo.authState.test {
            assertIs<AuthState.Authenticated>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signup InvalidEmail returns error`() = runTest {
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueRegister(
            ResultWithError.Failure(RegisterError.InvalidEmail(EmailValidationError.EmailTaken)),
        )
        val repo = createRepo(remoteDataSource = remote, scope = this)
        advanceUntilIdle()

        val result = repo.signup(credentials, "Test User")

        val failure = assertIs<ResultWithError.Failure<AuthSession, SignupRepositoryError>>(result)
        assertIs<SignupRepositoryError.InvalidEmail>(failure.error)
    }

    @Test
    fun `logout success tokens cleared and authState is Unauthenticated`() = runTest {
        val storage = AuthSessionStorageFake()
        val remote = RemoteAuthDataSourceFake()
        val repo = createRepo(remoteDataSource = remote, sessionStorage = storage, scope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        val result = repo.logout()

        assertIs<ResultWithError.Success<kotlin.Unit, *>>(result)
        repo.authState.test {
            assertIs<AuthState.Unauthenticated>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `logout remote failure clears session and returns RemoteOperationFailed`() = runTest {
        val storage = AuthSessionStorageFake()
        val remote = RemoteAuthDataSourceFake()
        val repo = createRepo(remoteDataSource = remote, sessionStorage = storage, scope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        remote.enqueueLogout(
            ResultWithError.Failure<kotlin.Unit, LogoutError>(
                LogoutError.RemoteDataSource(
                    RemoteDataSourceError.ServerError,
                ),
            ),
        )
        val result = repo.logout()

        val failure =
            assertIs<ResultWithError.Failure<kotlin.Unit, LogoutRepositoryError>>(result)
        assertIs<LogoutRepositoryError.RemoteOperationFailed>(failure.error)
        repo.authState.test {
            assertIs<AuthState.Unauthenticated>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshToken success new tokens saved and authState updated`() = runTest {
        val storage = AuthSessionStorageFake()
        val repo = createRepo(sessionStorage = storage, scope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        val newTokens = AuthTokens("new-access", "new-refresh")
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueRefresh(ResultWithError.Success(newTokens))
        val repo2 = createRepo(remoteDataSource = remote, sessionStorage = storage, scope = this)
        advanceUntilIdle()

        val result = repo2.refreshToken()

        assertIs<ResultWithError.Success<AuthTokens, *>>(result)
    }

    @Test
    fun `refreshToken TokenExpired returns error and storage unchanged`() = runTest {
        val storage = AuthSessionStorageFake()
        val repo = createRepo(sessionStorage = storage, scope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        val remote = RemoteAuthDataSourceFake()
        remote.enqueueRefresh(
            ResultWithError.Failure<AuthTokens, RefreshError>(RefreshError.TokenExpired),
        )
        val repo2 = createRepo(remoteDataSource = remote, sessionStorage = storage, scope = this)
        advanceUntilIdle()

        val result = repo2.refreshToken()

        val failure = assertIs<ResultWithError.Failure<AuthTokens, RefreshRepositoryError>>(result)
        assertIs<RefreshRepositoryError.TokenExpired>(failure.error)
    }

    @Test
    fun `initial auth state restored from storage when session exists`() = runTest {
        val storage = AuthSessionStorageFake()
        storage.enqueueGetAccessToken(
            ResultWithError.Success<String?, AuthSessionStorageError>("access-token"),
        )
        storage.enqueueGetRefreshToken(
            ResultWithError.Success<String?, AuthSessionStorageError>("refresh-token"),
        )
        storage.enqueueGetAuthProvider(
            ResultWithError.Success<AuthProvider?, AuthSessionStorageError>(AuthProvider.EMAIL),
        )

        val repo = createRepo(sessionStorage = storage, scope = this)
        advanceUntilIdle()

        repo.authState.test {
            assertIs<AuthState.Authenticated>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
