package timur.gilfanov.messenger.auth.data.repository

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
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
    ) = AuthCleanupObserver(
        authRepository = { authRepository },
        settingsRepository = { settingsRepository },
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

            // Change access token but keep refresh token (which determines the key)
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

            // Start observer while Authenticated
            observer.start()

            // Transition to Unauthenticated immediately
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
}
