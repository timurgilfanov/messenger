package timur.gilfanov.messenger.auth.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.auth.LocalAuthDataSourceFake
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryFake
import timur.gilfanov.messenger.domain.usecase.settings.repository.DeleteUserDataRepositoryError

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class AuthCleanupObserverTest {

    private val session1 = AuthSession(
        tokens = AuthTokens(accessToken = "access-1", refreshToken = "refresh-1"),
        provider = AuthProvider.EMAIL,
    )
    private val session2 = AuthSession(
        tokens = AuthTokens(accessToken = "access-2", refreshToken = "refresh-2"),
        provider = AuthProvider.EMAIL,
    )

    private fun createObserver(
        authRepository: AuthRepositoryFake,
        settingsRepository: SettingsRepositoryFake,
        scope: CoroutineScope,
        localAuthDataSource: LocalAuthDataSourceFake = LocalAuthDataSourceFake(),
    ) = AuthCleanupObserver(
        authRepository = { authRepository },
        settingsRepository = { settingsRepository },
        localAuthDataSource = { localAuthDataSource },
        scope = scope,
        logger = NoOpLogger(),
    )

    @Test
    fun `when transitioning Authenticated to Unauthenticated then cleanup is called`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val observer = createObserver(authRepository, settingsRepository, backgroundScope)

            observer.start()

            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(session1.toUserScopeKey(), settingsRepository.lastDeleteUserDataKey)
        }

    @Test
    fun `when user scope key changes then cleanup is called for old key`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val observer = createObserver(authRepository, settingsRepository, backgroundScope)

            observer.start()

            authRepository.setState(AuthState.Authenticated(session2))

            assertEquals(session1.toUserScopeKey(), settingsRepository.lastDeleteUserDataKey)
        }

    @Test
    fun `when user scope key changes and cleanup succeeds then pending marker is cleared`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val localAuthDataSource = LocalAuthDataSourceFake()
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()
            authRepository.setState(AuthState.Authenticated(session2))

            assertNull(localAuthDataSource.pendingCleanupKey)
        }

    @Test
    fun `when user scope key changes and cleanup fails then pending marker is kept for retry`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(
                initialSettings = Settings(UiLanguage.English),
                deleteUserDataResult = ResultWithError.Failure(
                    DeleteUserDataRepositoryError.LocalOperationFailed(
                        LocalStorageError.AccessDenied,
                    ),
                ),
            )
            val localAuthDataSource = LocalAuthDataSourceFake()
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()
            authRepository.setState(AuthState.Authenticated(session2))

            assertEquals(session1.toUserScopeKey(), localAuthDataSource.pendingCleanupKey)
        }

    @Test
    fun `when transitioning Unauthenticated to Authenticated then NO cleanup`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val observer = createObserver(authRepository, settingsRepository, backgroundScope)

            observer.start()

            authRepository.setState(AuthState.Authenticated(session1))

            assertNull(settingsRepository.lastDeleteUserDataKey)
        }

    @Test
    fun `when token changes but scope key remains same then NO cleanup`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val observer = createObserver(authRepository, settingsRepository, backgroundScope)

            observer.start()

            val session1Updated = session1.copy(
                tokens = session1.tokens.copy(accessToken = "access-1-updated"),
            )
            authRepository.setState(AuthState.Authenticated(session1Updated))

            assertNull(settingsRepository.lastDeleteUserDataKey)
        }

    @Test
    fun `when start Authenticated and immediate transition to Unauth then cleanup is called`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val observer = createObserver(authRepository, settingsRepository, backgroundScope)

            observer.start()

            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(session1.toUserScopeKey(), settingsRepository.lastDeleteUserDataKey)
        }

    @Test
    fun `when starting Authenticated and no transition occurs then NO cleanup`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val observer = createObserver(authRepository, settingsRepository, backgroundScope)

            observer.start()

            assertNull(settingsRepository.lastDeleteUserDataKey)
        }

    @Test
    fun `when start is called twice then cleanup is triggered only once per transition`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val observer = createObserver(authRepository, settingsRepository, backgroundScope)

            observer.start()
            observer.start()

            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(1, settingsRepository.deleteUserDataCallCount)
            assertEquals(session1.toUserScopeKey(), settingsRepository.lastDeleteUserDataKey)
        }

    @Test
    fun `when deleteUserData fails then observer continues observing subsequent transitions`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(
                initialSettings = Settings(UiLanguage.English),
                deleteUserDataResult = ResultWithError.Failure(
                    DeleteUserDataRepositoryError.LocalOperationFailed(
                        LocalStorageError.AccessDenied,
                    ),
                ),
            )
            val observer = createObserver(authRepository, settingsRepository, backgroundScope)

            observer.start()
            authRepository.setState(AuthState.Unauthenticated)
            assertEquals(1, settingsRepository.deleteUserDataCallCount)

            authRepository.setState(AuthState.Authenticated(session1))
            authRepository.setState(AuthState.Unauthenticated)
            assertEquals(2, settingsRepository.deleteUserDataCallCount)
        }

    @Test
    fun `when starting Unauthenticated with pending key then cleanup runs and key is cleared`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val localAuthDataSource = LocalAuthDataSourceFake().apply {
                pendingCleanupKey = session1.toUserScopeKey()
            }
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()

            assertEquals(session1.toUserScopeKey(), settingsRepository.lastDeleteUserDataKey)
            assertNull(localAuthDataSource.pendingCleanupKey)
        }

    @Test
    fun `when starting Authenticated with matching pending key then marker is preserved`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val localAuthDataSource = LocalAuthDataSourceFake().apply {
                pendingCleanupKey = session1.toUserScopeKey()
            }
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()

            assertNull(settingsRepository.lastDeleteUserDataKey)
            assertEquals(session1.toUserScopeKey(), localAuthDataSource.pendingCleanupKey)
        }

    @Test
    fun `when starting with no pending cleanup key then no extra cleanup occurs`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val localAuthDataSource = LocalAuthDataSourceFake()
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()

            assertNull(settingsRepository.lastDeleteUserDataKey)
        }

    @Test
    fun `when Authenticated has pending key for different user then runs cleanup and clears key`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session2))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val localAuthDataSource = LocalAuthDataSourceFake().apply {
                pendingCleanupKey = session1.toUserScopeKey()
            }
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()

            assertEquals(session1.toUserScopeKey(), settingsRepository.lastDeleteUserDataKey)
            assertNull(localAuthDataSource.pendingCleanupKey)
        }

    @Test
    fun `when deleteUserData fails during startup recovery then pending key is kept for retry`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Unauthenticated)
            val settingsRepository = SettingsRepositoryFake(
                initialSettings = Settings(UiLanguage.English),
                deleteUserDataResult = ResultWithError.Failure(
                    DeleteUserDataRepositoryError.LocalOperationFailed(
                        LocalStorageError.AccessDenied,
                    ),
                ),
            )
            val localAuthDataSource = LocalAuthDataSourceFake().apply {
                pendingCleanupKey = session1.toUserScopeKey()
            }
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()

            assertEquals(session1.toUserScopeKey(), settingsRepository.lastDeleteUserDataKey)
            assertEquals(session1.toUserScopeKey(), localAuthDataSource.pendingCleanupKey)
        }

    @Test
    fun `when deleteUserData fails during logout transition then pending key is kept for retry`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(
                initialSettings = Settings(UiLanguage.English),
                deleteUserDataResult = ResultWithError.Failure(
                    DeleteUserDataRepositoryError.LocalOperationFailed(
                        LocalStorageError.AccessDenied,
                    ),
                ),
            )
            val localAuthDataSource = LocalAuthDataSourceFake()
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()
            localAuthDataSource.pendingCleanupKey = session1.toUserScopeKey()
            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(session1.toUserScopeKey(), localAuthDataSource.pendingCleanupKey)
        }

    @Test
    fun `when logout cleanup runs then pending key is cleared after deleteUserData`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val localAuthDataSource = LocalAuthDataSourceFake().apply {
                pendingCleanupKey = session1.toUserScopeKey()
            }
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()
            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(session1.toUserScopeKey(), settingsRepository.lastDeleteUserDataKey)
            assertNull(localAuthDataSource.pendingCleanupKey)
        }

    @Test
    fun `when second user logs out while first cleanup is running then both cleanups complete`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val observer = createObserver(authRepository, settingsRepository, backgroundScope)

            observer.start()

            authRepository.setState(AuthState.Unauthenticated)
            authRepository.setState(AuthState.Authenticated(session2))
            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(2, settingsRepository.deleteUserDataCallCount)
        }

    @Test
    fun `when pending marker exists and scope changes then marker is preserved and cleanup runs`() =
        runTest(UnconfinedTestDispatcher()) {
            val session3 = AuthSession(
                tokens = AuthTokens(accessToken = "access-3", refreshToken = "refresh-3"),
                provider = AuthProvider.EMAIL,
            )
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val localAuthDataSource = LocalAuthDataSourceFake()
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()
            localAuthDataSource.pendingCleanupKey = session2.toUserScopeKey()
            authRepository.setState(AuthState.Authenticated(session3))

            assertEquals(session2.toUserScopeKey(), localAuthDataSource.pendingCleanupKey)
            assertEquals(session1.toUserScopeKey(), settingsRepository.lastDeleteUserDataKey)
        }

    @Test
    fun `when cleanup for user A succeeds but stored pending key is B then B key is not cleared`() =
        runTest(UnconfinedTestDispatcher()) {
            val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
            val settingsRepository = SettingsRepositoryFake(Settings(UiLanguage.English))
            val localAuthDataSource = LocalAuthDataSourceFake()
            val observer = createObserver(
                authRepository,
                settingsRepository,
                backgroundScope,
                localAuthDataSource,
            )

            observer.start()

            localAuthDataSource.pendingCleanupKey = session2.toUserScopeKey()

            authRepository.setState(AuthState.Unauthenticated)

            assertEquals(session2.toUserScopeKey(), localAuthDataSource.pendingCleanupKey)
        }
}
