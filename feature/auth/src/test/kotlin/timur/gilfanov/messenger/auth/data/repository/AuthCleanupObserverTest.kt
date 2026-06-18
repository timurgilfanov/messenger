package timur.gilfanov.messenger.auth.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.UserScopedDataCleaner
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class AuthCleanupObserverTest {

    private val session1 = AuthSession(
        tokens = AuthTokens(accessToken = "access-1", refreshToken = "refresh-1"),
        provider = AuthProvider.EMAIL,
        sessionId = "session-id-1",
    )
    private val session2 = AuthSession(
        tokens = AuthTokens(accessToken = "access-2", refreshToken = "refresh-2"),
        provider = AuthProvider.EMAIL,
        sessionId = "session-id-2",
    )

    private fun createObserver(
        authRepository: AuthRepositoryFake,
        dataCleaner: UserScopedDataCleanerFake,
        scope: CoroutineScope,
    ) = AuthCleanupObserverImpl(
        authRepository = { authRepository },
        dataCleaners = { setOf<UserScopedDataCleaner>(dataCleaner) },
        scope = scope,
        logger = NoOpLogger(),
    )

    @Test
    fun `when transitioning Authenticated to Unauthenticated then cleanup is called`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val dataCleaner = UserScopedDataCleanerFake()
            val observer = createObserver(authRepository, dataCleaner, backgroundScope)

            observer.start()

            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(session1.toUserScopeKey(), dataCleaner.lastDeleteUserDataKey)
        }

    @Test
    fun `when user scope key changes then cleanup is called for old key`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val dataCleaner = UserScopedDataCleanerFake()
            val observer = createObserver(authRepository, dataCleaner, backgroundScope)

            observer.start()

            authRepository.setState(AuthState.Authenticated(session2))

            assertEquals(session1.toUserScopeKey(), dataCleaner.lastDeleteUserDataKey)
        }

    @Test
    fun `when transitioning Unauthenticated to Authenticated then NO cleanup`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)
            val dataCleaner = UserScopedDataCleanerFake()
            val observer = createObserver(authRepository, dataCleaner, backgroundScope)

            observer.start()

            authRepository.setState(AuthState.Authenticated(session1))

            assertNull(dataCleaner.lastDeleteUserDataKey)
        }

    @Test
    fun `when only access token changes then NO cleanup`() = runTest(UnconfinedTestDispatcher()) {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
        val dataCleaner = UserScopedDataCleanerFake()
        val observer = createObserver(authRepository, dataCleaner, backgroundScope)

        observer.start()

        val session1AccessUpdated = session1.copy(
            tokens = session1.tokens.copy(accessToken = "access-1-updated"),
        )
        authRepository.setState(AuthState.Authenticated(session1AccessUpdated))

        assertNull(dataCleaner.lastDeleteUserDataKey)
    }

    @Test
    fun `when both tokens rotate but session ID is unchanged then NO cleanup`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val dataCleaner = UserScopedDataCleanerFake()
            val observer = createObserver(authRepository, dataCleaner, backgroundScope)

            observer.start()

            val session1TokensRotated = session1.copy(
                tokens = AuthTokens(
                    accessToken = "access-1-rotated",
                    refreshToken = "refresh-1-rotated",
                ),
            )
            authRepository.setState(AuthState.Authenticated(session1TokensRotated))

            assertNull(dataCleaner.lastDeleteUserDataKey)
        }

    @Test
    fun `when start Authenticated and immediate transition to Unauth then cleanup is called`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val dataCleaner = UserScopedDataCleanerFake()
            val observer = createObserver(authRepository, dataCleaner, backgroundScope)

            // Start observer while Authenticated
            observer.start()

            // Transition to Unauthenticated immediately
            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(session1.toUserScopeKey(), dataCleaner.lastDeleteUserDataKey)
        }

    @Test
    fun `when starting Authenticated and no transition occurs then NO cleanup`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val dataCleaner = UserScopedDataCleanerFake()
            val observer = createObserver(authRepository, dataCleaner, backgroundScope)

            observer.start()

            assertNull(dataCleaner.lastDeleteUserDataKey)
        }

    @Test
    fun `when start is called twice then cleanup is triggered only once per transition`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val dataCleaner = UserScopedDataCleanerFake()
            val observer = createObserver(authRepository, dataCleaner, backgroundScope)

            observer.start()
            observer.start()

            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(1, dataCleaner.deleteUserDataCallCount)
            assertEquals(session1.toUserScopeKey(), dataCleaner.lastDeleteUserDataKey)
        }

    @Test
    fun `when deleteUserData fails then observer continues observing subsequent transitions`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val dataCleaner = UserScopedDataCleanerFake(
                deleteUserDataResult = ResultWithError.Failure(
                    LocalStorageError.AccessDenied,
                ),
            )
            val observer = createObserver(authRepository, dataCleaner, backgroundScope)

            observer.start()
            authRepository.setState(AuthState.Unauthenticated)
            assertEquals(1, dataCleaner.deleteUserDataCallCount)

            authRepository.setState(AuthState.Authenticated(session1))
            authRepository.setState(AuthState.Unauthenticated)
            assertEquals(2, dataCleaner.deleteUserDataCallCount)
        }

    private class UserScopedDataCleanerFake(
        private val deleteUserDataResult: ResultWithError<Unit, LocalStorageError> =
            ResultWithError.Success(Unit),
    ) : UserScopedDataCleaner {
        var lastDeleteUserDataKey: UserScopeKey? = null
            private set
        var deleteUserDataCallCount: Int = 0
            private set

        override suspend fun deleteUserData(
            userKey: UserScopeKey,
        ): ResultWithError<Unit, LocalStorageError> {
            lastDeleteUserDataKey = userKey
            deleteUserDataCallCount++
            return deleteUserDataResult
        }
    }
}
