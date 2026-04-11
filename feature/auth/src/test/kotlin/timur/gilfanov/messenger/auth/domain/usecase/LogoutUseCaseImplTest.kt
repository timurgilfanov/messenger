package timur.gilfanov.messenger.auth.domain.usecase

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryFake
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.repository.DeleteUserDataRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class LogoutUseCaseImplTest {

    private fun createUseCase(
        repository: AuthRepositoryFake = AuthRepositoryFake(),
        settingsRepository: SettingsRepository = SettingsRepositoryStub(),
    ) = LogoutUseCaseImpl(repository, settingsRepository, NoOpLogger())

    @Test
    fun `success returns Success`() = runTest {
        val repository = AuthRepositoryFake().apply {
            defaultLogoutResult = ResultWithError.Success(Unit)
        }
        val result = createUseCase(repository)()
        assertEquals(ResultWithError.Success(Unit), result)
    }

    @Test
    fun `local error maps to LocalOperationFailed`() = runTest {
        val localError = LocalStorageError.UnknownError(Exception("disk full"))
        val repository = AuthRepositoryFake().apply {
            defaultLogoutResult =
                ResultWithError.Failure(LogoutRepositoryError.LocalOperationFailed(localError))
        }
        val result = createUseCase(repository)()
        assertIs<ResultWithError.Failure<Unit, LogoutError>>(result)
        assertIs<LogoutError.LocalOperationFailed>(result.error)
        val resultLocalError = result.error as LogoutError.LocalOperationFailed
        assertEquals(localError, resultLocalError.error)
    }

    @Test
    fun `remote error maps to RemoteOperationFailed`() = runTest {
        val remoteError = RemoteError.Failed.NetworkNotAvailable
        val repository = AuthRepositoryFake().apply {
            defaultLogoutResult =
                ResultWithError.Failure(LogoutRepositoryError.RemoteOperationFailed(remoteError))
        }
        val result = createUseCase(repository)()
        assertIs<ResultWithError.Failure<Unit, LogoutError>>(result)
        assertIs<LogoutError.RemoteOperationFailed>(result.error)
        val resultRemoteError = result.error as LogoutError.RemoteOperationFailed
        assertEquals(remoteError, resultRemoteError.error)
    }

    @Test
    fun `cleanup called with correct user key on logout`() = runTest {
        val session = AuthSession(
            tokens = AuthTokens(accessToken = "access-1", refreshToken = "refresh-1"),
            provider = AuthProvider.EMAIL,
        )
        val expectedKey = session.toUserScopeKey()
        val repository = AuthRepositoryFake(initialAuthState = AuthState.Authenticated(session))
        val settingsRepository = SettingsRepositoryFake(
            initialSettings = timur.gilfanov.messenger.domain.entity.settings.Settings(
                uiLanguage = timur.gilfanov.messenger.domain.entity.settings.UiLanguage.English,
            ),
        )
        createUseCase(repository, settingsRepository)()
        assertNotNull(settingsRepository.lastDeleteUserDataKey)
        assertEquals(expectedKey, settingsRepository.lastDeleteUserDataKey)
    }

    @Test
    fun `cleanup failure does not block logout`() = runTest {
        val session = AuthSession(
            tokens = AuthTokens(accessToken = "access-2", refreshToken = "refresh-2"),
            provider = AuthProvider.EMAIL,
        )
        val repository = AuthRepositoryFake(
            initialAuthState = AuthState.Authenticated(session),
        ).apply {
            defaultLogoutResult = ResultWithError.Success(Unit)
        }
        val settingsRepository = SettingsRepositoryFake(
            initialSettings = timur.gilfanov.messenger.domain.entity.settings.Settings(
                uiLanguage = timur.gilfanov.messenger.domain.entity.settings.UiLanguage.English,
            ),
            deleteUserDataResult = ResultWithError.Failure(
                DeleteUserDataRepositoryError.LocalOperationFailed(
                    timur.gilfanov.messenger.domain.usecase.common.LocalStorageError.UnknownError(
                        Exception("disk full"),
                    ),
                ),
            ),
        )
        val result = createUseCase(repository, settingsRepository)()
        assertEquals(ResultWithError.Success(Unit), result)
    }
}
