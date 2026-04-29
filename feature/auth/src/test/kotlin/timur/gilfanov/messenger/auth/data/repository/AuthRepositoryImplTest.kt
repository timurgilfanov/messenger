package timur.gilfanov.messenger.auth.data.repository

import app.cash.turbine.test
import dagger.Lazy
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSourceError
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSourceFake
import timur.gilfanov.messenger.auth.data.source.remote.LoginWithCredentialsError
import timur.gilfanov.messenger.auth.data.source.remote.LogoutError
import timur.gilfanov.messenger.auth.data.source.remote.RefreshError
import timur.gilfanov.messenger.auth.data.source.remote.RegisterError
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSourceFake
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
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupEmailError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryStub

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class AuthRepositoryImplTest {

    private val credentials = Credentials(Email("test@example.com"), Password("password123"))
    private val googleIdToken = GoogleIdToken("google-id-token")

    private fun createRepo(
        remoteDataSource: RemoteAuthDataSourceFake = RemoteAuthDataSourceFake(),
        sessionStorage: LocalAuthDataSourceFake = LocalAuthDataSourceFake(),
        testScope: kotlinx.coroutines.test.TestScope,
    ): AuthRepositoryImpl {
        val logger = NoOpLogger()
        val authRepositoryLazy = object : Lazy<AuthRepository> {
            private var repo: AuthRepository? = null
            fun set(r: AuthRepository) {
                repo = r
            }
            override fun get(): AuthRepository = repo!!
        }
        val cleanupObserver = AuthCleanupObserver(
            authRepository = authRepositoryLazy,
            settingsRepository = { SettingsRepositoryStub() },
            localAuthDataSource = { sessionStorage },
            scope = testScope.backgroundScope,
            logger = logger,
        )
        val repo = AuthRepositoryImpl(
            remoteDataSource = remoteDataSource,
            localDataSource = sessionStorage,
            cleanupObserver = { cleanupObserver },
            coroutineScope = testScope,
            logger = logger,
        )
        authRepositoryLazy.set(repo)
        return repo
    }

    @Test
    fun `loginWithCredentials success stores session and sets Authenticated EMAIL provider`() =
        runTest {
            val storage = LocalAuthDataSourceFake()
            val repo = createRepo(sessionStorage = storage, testScope = this)
            advanceUntilIdle()

            val result = repo.loginWithCredentials(credentials)

            assertIs<ResultWithError.Success<AuthSession, LoginRepositoryError>>(result)
            kotlin.test.assertEquals(AuthProvider.EMAIL, result.data.provider)
            repo.authState.test {
                assertIs<AuthState.Authenticated>(awaitItem())
            }
        }

    @Test
    fun `loginWithCredentials InvalidCredentials returns error and storage not called`() = runTest {
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueLoginWithCredentials(
            ResultWithError.Failure(LoginWithCredentialsError.InvalidCredentials),
        )
        val storage = LocalAuthDataSourceFake()
        val repo = createRepo(remoteDataSource = remote, sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo.loginWithCredentials(credentials)

        val failure = assertIs<ResultWithError.Failure<AuthSession, LoginRepositoryError>>(result)
        assertIs<LoginRepositoryError.InvalidCredentials>(failure.error)
    }

    @Test
    fun `loginWithCredentials storage failure returns LocalOperationFailed`() = runTest {
        val storage = LocalAuthDataSourceFake()
        storage.enqueueSaveSession(
            ResultWithError.Failure(LocalAuthDataSourceError.AccessDenied),
        )
        val repo = createRepo(sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo.loginWithCredentials(credentials)

        val failure = assertIs<ResultWithError.Failure<AuthSession, LoginRepositoryError>>(result)
        assertIs<LoginRepositoryError.LocalOperationFailed>(failure.error)
    }

    @Test
    fun `loginWithCredentials storage full maps to LocalStorageError StorageFull`() = runTest {
        val storage = LocalAuthDataSourceFake()
        storage.enqueueSaveSession(
            ResultWithError.Failure(LocalAuthDataSourceError.StorageFull),
        )
        val repo = createRepo(sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo.loginWithCredentials(credentials)

        val failure = assertIs<ResultWithError.Failure<AuthSession, LoginRepositoryError>>(result)
        val error = assertIs<LoginRepositoryError.LocalOperationFailed>(failure.error)
        assertIs<LocalStorageError.StorageFull>(error.error)
    }

    @Test
    fun `loginWithGoogle success stores session and sets Authenticated GOOGLE provider`() =
        runTest {
            val storage = LocalAuthDataSourceFake()
            val repo = createRepo(sessionStorage = storage, testScope = this)
            advanceUntilIdle()

            val result = repo.loginWithGoogle(googleIdToken)

            assertIs<ResultWithError.Success<AuthSession, *>>(result)
            kotlin.test.assertEquals(AuthProvider.GOOGLE, result.data.provider)
            repo.authState.test {
                assertIs<AuthState.Authenticated>(awaitItem())
            }
        }

    @Test
    fun `signup success session saved and authState is Authenticated`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val repo = createRepo(sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo.signup(credentials, "Test User")

        assertIs<ResultWithError.Success<AuthSession, *>>(result)
        repo.authState.test {
            assertIs<AuthState.Authenticated>(awaitItem())
        }
    }

    @Test
    fun `signup InvalidEmail returns error`() = runTest {
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueRegister(
            ResultWithError.Failure(RegisterError.InvalidEmail(SignupEmailError.EmailTaken)),
        )
        val repo = createRepo(remoteDataSource = remote, testScope = this)
        advanceUntilIdle()

        val result = repo.signup(credentials, "Test User")

        val failure = assertIs<ResultWithError.Failure<AuthSession, SignupRepositoryError>>(result)
        assertIs<SignupRepositoryError.InvalidEmail>(failure.error)
    }

    @Test
    fun `logout success tokens cleared and authState is Unauthenticated`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val remote = RemoteAuthDataSourceFake()
        val repo = createRepo(remoteDataSource = remote, sessionStorage = storage, testScope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        val result = repo.logout()

        assertIs<ResultWithError.Success<Unit, *>>(result)
        repo.authState.test {
            assertIs<AuthState.Unauthenticated>(awaitItem())
        }
    }

    @Test
    fun `logout remote failure clears session and returns RemoteOperationFailed`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val remote = RemoteAuthDataSourceFake()
        val repo = createRepo(remoteDataSource = remote, sessionStorage = storage, testScope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        remote.enqueueLogout(
            ResultWithError.Failure<Unit, LogoutError>(
                LogoutError.RemoteDataSource(
                    RemoteDataSourceError.ServerError,
                ),
            ),
        )
        val result = repo.logout()

        val failure =
            assertIs<ResultWithError.Failure<Unit, LogoutRepositoryError>>(result)
        assertIs<LogoutRepositoryError.RemoteOperationFailed>(failure.error)
        repo.authState.test {
            assertIs<AuthState.Unauthenticated>(awaitItem())
        }
    }

    @Test
    fun `logout clearSession failure returns LocalOperationFailed`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val remote = RemoteAuthDataSourceFake()
        val repo = createRepo(remoteDataSource = remote, sessionStorage = storage, testScope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        storage.enqueueClearSession(
            ResultWithError.Failure(LocalAuthDataSourceError.AccessDenied),
        )
        val result = repo.logout()

        val failure = assertIs<ResultWithError.Failure<Unit, LogoutRepositoryError>>(result)
        assertIs<LogoutRepositoryError.LocalOperationFailed>(failure.error)
        repo.authState.test {
            assertIs<AuthState.Authenticated>(awaitItem())
        }
    }

    @Test
    fun `refreshToken success new tokens saved and authState updated`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val repo = createRepo(sessionStorage = storage, testScope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        val newTokens = AuthTokens("new-access", "new-refresh")
        val remote = RemoteAuthDataSourceFake()
        remote.enqueueRefresh(ResultWithError.Success(newTokens))
        val repo2 =
            createRepo(remoteDataSource = remote, sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo2.refreshToken()

        assertIs<ResultWithError.Success<AuthTokens, *>>(result)
    }

    @Test
    fun `refreshToken TokenExpired returns error and storage unchanged`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val repo = createRepo(sessionStorage = storage, testScope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        val remote = RemoteAuthDataSourceFake()
        remote.enqueueRefresh(
            ResultWithError.Failure<AuthTokens, RefreshError>(RefreshError.TokenExpired),
        )
        val repo2 =
            createRepo(remoteDataSource = remote, sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        val result = repo2.refreshToken()

        val failure = assertIs<ResultWithError.Failure<AuthTokens, RefreshRepositoryError>>(result)
        assertIs<RefreshRepositoryError.TokenExpired>(failure.error)
    }

    @Test
    fun `initial auth state restored from storage when session exists`() = runTest {
        val storage = LocalAuthDataSourceFake()
        storage.enqueueGetAccessToken(ResultWithError.Success("access-token"))
        storage.enqueueGetRefreshToken(ResultWithError.Success("refresh-token"))
        storage.enqueueGetAuthProvider(ResultWithError.Success(AuthProvider.EMAIL))

        val repo = createRepo(sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        repo.authState.test {
            assertIs<AuthState.Authenticated>(awaitItem())
        }
    }

    @Test
    fun `logout saves pending cleanup key before clearing session`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val repo = createRepo(sessionStorage = storage, testScope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        val refreshTokenBeforeLogout = requireNotNull(storage.refreshToken)
        val expectedKey = AuthSession(
            tokens = AuthTokens(accessToken = "", refreshToken = refreshTokenBeforeLogout),
            provider = AuthProvider.EMAIL,
        ).toUserScopeKey()

        repo.logout()

        assertEquals(expectedKey, storage.pendingCleanupKey)
    }

    @Test
    fun `logout clears pending cleanup key when clearSession fails`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val repo = createRepo(sessionStorage = storage, testScope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        assertNotNull(storage.refreshToken)
        storage.enqueueClearSession(
            ResultWithError.Failure(LocalAuthDataSourceError.AccessDenied),
        )

        repo.logout()

        assertNull(storage.pendingCleanupKey)
    }

    @Test
    fun `refreshToken writes pending cleanup key for old scope when refresh token changes`() =
        runTest {
            val storage = LocalAuthDataSourceFake()
            val remote = RemoteAuthDataSourceFake()
            val repo =
                createRepo(remoteDataSource = remote, sessionStorage = storage, testScope = this)
            repo.loginWithCredentials(credentials)
            advanceUntilIdle()

            val oldRefreshToken = requireNotNull(storage.refreshToken)
            val oldKey = AuthSession(
                tokens = AuthTokens(accessToken = "", refreshToken = oldRefreshToken),
                provider = AuthProvider.EMAIL,
            ).toUserScopeKey()
            val newTokens = AuthTokens("new-access", "new-refresh")
            remote.enqueueRefresh(ResultWithError.Success(newTokens))

            repo.refreshToken()

            assertEquals(oldKey, storage.pendingCleanupKey)
        }

    @Test
    fun `refreshToken does not write pending cleanup key when scope key is unchanged`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val remote = RemoteAuthDataSourceFake()
        val repo = createRepo(remoteDataSource = remote, sessionStorage = storage, testScope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        val currentRefreshToken = requireNotNull(storage.refreshToken)
        val newTokens = AuthTokens("new-access", currentRefreshToken)
        remote.enqueueRefresh(ResultWithError.Success(newTokens))

        repo.refreshToken()

        assertNull(storage.pendingCleanupKey)
    }

    @Test
    fun `logout returns LocalOperationFailed when pending cleanup marker write fails`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val repo = createRepo(sessionStorage = storage, testScope = this)
        repo.loginWithCredentials(credentials)
        advanceUntilIdle()

        storage.enqueueSetPendingCleanupKey(
            ResultWithError.Failure(LocalAuthDataSourceError.AccessDenied),
        )

        val result = repo.logout()

        val failure = assertIs<ResultWithError.Failure<Unit, LogoutRepositoryError>>(result)
        assertIs<LogoutRepositoryError.LocalOperationFailed>(failure.error)
        repo.authState.test {
            assertIs<AuthState.Authenticated>(awaitItem())
        }
    }

    @Test
    fun `refreshToken returns SessionRevoked and does not save tokens after logout`() = runTest {
        val storage = LocalAuthDataSourceFake()
        val remote = RemoteAuthDataSourceFake()
        val repo =
            createRepo(remoteDataSource = remote, sessionStorage = storage, testScope = this)
        advanceUntilIdle()

        storage.enqueueGetRefreshToken(ResultWithError.Success("stale-refresh-token"))
        remote.enqueueRefresh(ResultWithError.Success(AuthTokens("new-access", "new-refresh")))

        val result = repo.refreshToken()

        val failure =
            assertIs<ResultWithError.Failure<AuthTokens, RefreshRepositoryError>>(result)
        assertIs<RefreshRepositoryError.SessionRevoked>(failure.error)
        assertNull(storage.accessToken)
        assertNull(storage.refreshToken)
    }
}
