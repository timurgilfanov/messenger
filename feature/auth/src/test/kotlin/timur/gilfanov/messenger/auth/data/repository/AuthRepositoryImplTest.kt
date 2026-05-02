package timur.gilfanov.messenger.auth.data.repository

import app.cash.turbine.test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSource
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSourceError
import timur.gilfanov.messenger.auth.data.source.local.LocalAuthDataSourceFake
import timur.gilfanov.messenger.auth.data.source.remote.LoginWithCredentialsError
import timur.gilfanov.messenger.auth.data.source.remote.LoginWithGoogleError
import timur.gilfanov.messenger.auth.data.source.remote.LogoutError
import timur.gilfanov.messenger.auth.data.source.remote.RefreshError
import timur.gilfanov.messenger.auth.data.source.remote.RegisterError
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSource
import timur.gilfanov.messenger.auth.data.source.remote.RemoteAuthDataSourceFake
import timur.gilfanov.messenger.auth.data.source.remote.SignupWithGoogleError
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
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupEmailError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class AuthRepositoryImplTest {

    private val credentials = Credentials(Email("test@example.com"), Password("password123"))
    private val googleIdToken = GoogleIdToken("google-id-token")

    private fun createRepo(
        remoteDataSource: RemoteAuthDataSource = RemoteAuthDataSourceFake(),
        sessionStorage: LocalAuthDataSource = LocalAuthDataSourceFake(),
        testScope: kotlinx.coroutines.test.TestScope,
    ): AuthRepositoryImpl = AuthRepositoryImpl(
        remoteDataSource = remoteDataSource,
        localDataSource = sessionStorage,
        coroutineScope = testScope,
        logger = NoOpLogger(),
    )

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
    fun `refreshToken returns SessionRevoked when logout clears session first`() = runTest {
        val initialSession = AuthSession(
            tokens = AuthTokens("old-access", "old-refresh"),
            provider = AuthProvider.EMAIL,
        )
        val newTokens = AuthTokens("new-access", "new-refresh")
        val storage = RefreshLogoutRaceLocalAuthDataSource(initialSession)
        val remote = RefreshLogoutRaceRemoteAuthDataSource(
            refreshCanComplete = storage.clearSessionStarted,
            refreshedTokens = newTokens,
        )
        val repo = createRepo(
            remoteDataSource = remote,
            sessionStorage = storage,
            testScope = this,
        )
        advanceUntilIdle()

        val refreshResult = async { repo.refreshToken() }
        advanceUntilIdle()
        val logoutResult = async { repo.logout() }
        storage.clearSessionStarted.await()
        advanceUntilIdle()
        storage.finishClearSession.complete(Unit)

        assertIs<ResultWithError.Success<Unit, LogoutRepositoryError>>(logoutResult.await())
        val refreshFailure =
            assertIs<ResultWithError.Failure<AuthTokens, RefreshRepositoryError>>(
                refreshResult.await(),
            )
        assertIs<RefreshRepositoryError.SessionRevoked>(refreshFailure.error)
        assertNull(storage.accessToken)
        assertNull(storage.refreshToken)
        repo.authState.test {
            assertIs<AuthState.Unauthenticated>(awaitItem())
        }
    }

    @Test
    fun `refresh during logout-then-login returns SessionRevoked without overwriting tokens`() =
        runTest {
            val initialAccessToken = "a-access"
            val initialRefreshToken = "a-refresh"
            val storage = LocalAuthDataSourceFake().apply {
                enqueueGetAccessToken(ResultWithError.Success(initialAccessToken))
                enqueueGetRefreshToken(ResultWithError.Success(initialRefreshToken))
                enqueueGetAuthProvider(ResultWithError.Success(AuthProvider.EMAIL))
            }
            val refreshGate = CompletableDeferred<Unit>()
            val refreshedAtokens = AuthTokens("a-refreshed-access", "a-refreshed-refresh")
            val newLoginBtokens = AuthTokens("b-access", "b-refresh")
            val remote = StaleRefreshRemote(
                refreshGate = refreshGate,
                refreshedTokens = refreshedAtokens,
                loginTokens = newLoginBtokens,
            )

            val repo = createRepo(
                remoteDataSource = remote,
                sessionStorage = storage,
                testScope = this,
            )
            advanceUntilIdle()

            val refreshDeferred = async { repo.refreshToken() }
            advanceUntilIdle()

            val logoutResult = repo.logout()
            assertIs<ResultWithError.Success<Unit, LogoutRepositoryError>>(logoutResult)
            val loginResult = repo.loginWithCredentials(credentials)
            assertIs<ResultWithError.Success<AuthSession, LoginRepositoryError>>(loginResult)
            advanceUntilIdle()

            refreshGate.complete(Unit)
            advanceUntilIdle()

            val refreshFailure =
                assertIs<ResultWithError.Failure<AuthTokens, RefreshRepositoryError>>(
                    refreshDeferred.await(),
                )
            assertIs<RefreshRepositoryError.SessionRevoked>(refreshFailure.error)
            repo.authState.test {
                val state = assertIs<AuthState.Authenticated>(awaitItem())
                kotlin.test.assertEquals(newLoginBtokens, state.session.tokens)
            }
        }

    @Test
    fun `logout during concurrent login does not wipe the new session`() = runTest {
        val initialAccessToken = "a-access"
        val initialRefreshToken = "a-refresh"
        val storage = LocalAuthDataSourceFake().apply {
            enqueueGetAccessToken(ResultWithError.Success(initialAccessToken))
            enqueueGetRefreshToken(ResultWithError.Success(initialRefreshToken))
            enqueueGetAuthProvider(ResultWithError.Success(AuthProvider.EMAIL))
        }
        val logoutGate = CompletableDeferred<Unit>()
        val newLoginBtokens = AuthTokens("b-access", "b-refresh")
        val remote = LogoutRaceRemote(
            logoutGate = logoutGate,
            loginTokens = newLoginBtokens,
        )

        val repo = createRepo(
            remoteDataSource = remote,
            sessionStorage = storage,
            testScope = this,
        )
        advanceUntilIdle()

        val logoutDeferred = async { repo.logout() }
        advanceUntilIdle()

        val loginResult = repo.loginWithCredentials(credentials)
        assertIs<ResultWithError.Success<AuthSession, LoginRepositoryError>>(loginResult)
        advanceUntilIdle()

        logoutGate.complete(Unit)
        advanceUntilIdle()

        assertIs<ResultWithError.Success<Unit, LogoutRepositoryError>>(logoutDeferred.await())
        repo.authState.test {
            val state = assertIs<AuthState.Authenticated>(awaitItem())
            kotlin.test.assertEquals(newLoginBtokens, state.session.tokens)
        }
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
}

private class RefreshLogoutRaceLocalAuthDataSource(initialSession: AuthSession) :
    LocalAuthDataSource {
    var accessToken: String? = initialSession.tokens.accessToken
        private set
    var refreshToken: String? = initialSession.tokens.refreshToken
        private set
    private var authProvider: AuthProvider? = initialSession.provider

    val clearSessionStarted = CompletableDeferred<Unit>()
    val finishClearSession = CompletableDeferred<Unit>()

    override suspend fun getAccessToken(): ResultWithError<String?, LocalAuthDataSourceError> =
        ResultWithError.Success(accessToken)

    override suspend fun getRefreshToken(): ResultWithError<String?, LocalAuthDataSourceError> =
        ResultWithError.Success(refreshToken)

    override suspend fun getAuthProvider(): ResultWithError<
        AuthProvider?,
        LocalAuthDataSourceError,
        > =
        ResultWithError.Success(authProvider)

    override suspend fun saveTokens(
        tokens: AuthTokens,
    ): ResultWithError<Unit, LocalAuthDataSourceError> {
        accessToken = tokens.accessToken
        refreshToken = tokens.refreshToken
        return ResultWithError.Success(Unit)
    }

    override suspend fun saveSession(
        session: AuthSession,
    ): ResultWithError<Unit, LocalAuthDataSourceError> {
        accessToken = session.tokens.accessToken
        refreshToken = session.tokens.refreshToken
        authProvider = session.provider
        return ResultWithError.Success(Unit)
    }

    override suspend fun clearSession(): ResultWithError<Unit, LocalAuthDataSourceError> {
        accessToken = null
        refreshToken = null
        authProvider = null
        clearSessionStarted.complete(Unit)
        finishClearSession.await()
        return ResultWithError.Success(Unit)
    }
}

private class RefreshLogoutRaceRemoteAuthDataSource(
    private val refreshCanComplete: CompletableDeferred<Unit>,
    private val refreshedTokens: AuthTokens,
) : RemoteAuthDataSource {
    override suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthTokens, LoginWithCredentialsError> =
        ResultWithError.Success(refreshedTokens)

    override suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthTokens, LoginWithGoogleError> = ResultWithError.Success(refreshedTokens)

    override suspend fun signupWithGoogle(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<AuthTokens, SignupWithGoogleError> = ResultWithError.Success(refreshedTokens)

    override suspend fun register(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthTokens, RegisterError> = ResultWithError.Success(refreshedTokens)

    override suspend fun refresh(refreshToken: String): ResultWithError<AuthTokens, RefreshError> {
        refreshCanComplete.await()
        return ResultWithError.Success(refreshedTokens)
    }

    override suspend fun logout(accessToken: String): ResultWithError<Unit, LogoutError> =
        ResultWithError.Success(Unit)
}

private class StaleRefreshRemote(
    private val refreshGate: CompletableDeferred<Unit>,
    private val refreshedTokens: AuthTokens,
    private val loginTokens: AuthTokens,
) : RemoteAuthDataSource {
    override suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthTokens, LoginWithCredentialsError> = ResultWithError.Success(loginTokens)

    override suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthTokens, LoginWithGoogleError> = ResultWithError.Success(loginTokens)

    override suspend fun signupWithGoogle(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<AuthTokens, SignupWithGoogleError> = ResultWithError.Success(loginTokens)

    override suspend fun register(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthTokens, RegisterError> = ResultWithError.Success(loginTokens)

    override suspend fun refresh(refreshToken: String): ResultWithError<AuthTokens, RefreshError> {
        refreshGate.await()
        return ResultWithError.Success(refreshedTokens)
    }

    override suspend fun logout(accessToken: String): ResultWithError<Unit, LogoutError> =
        ResultWithError.Success(Unit)
}

private class LogoutRaceRemote(
    private val logoutGate: CompletableDeferred<Unit>,
    private val loginTokens: AuthTokens,
) : RemoteAuthDataSource {
    override suspend fun loginWithCredentials(
        credentials: Credentials,
    ): ResultWithError<AuthTokens, LoginWithCredentialsError> = ResultWithError.Success(loginTokens)

    override suspend fun loginWithGoogle(
        idToken: GoogleIdToken,
    ): ResultWithError<AuthTokens, LoginWithGoogleError> = ResultWithError.Success(loginTokens)

    override suspend fun signupWithGoogle(
        idToken: GoogleIdToken,
        name: String,
    ): ResultWithError<AuthTokens, SignupWithGoogleError> = ResultWithError.Success(loginTokens)

    override suspend fun register(
        credentials: Credentials,
        name: String,
    ): ResultWithError<AuthTokens, RegisterError> = ResultWithError.Success(loginTokens)

    override suspend fun refresh(refreshToken: String): ResultWithError<AuthTokens, RefreshError> =
        ResultWithError.Success(loginTokens)

    override suspend fun logout(accessToken: String): ResultWithError<Unit, LogoutError> {
        logoutGate.await()
        return ResultWithError.Success(Unit)
    }
}
