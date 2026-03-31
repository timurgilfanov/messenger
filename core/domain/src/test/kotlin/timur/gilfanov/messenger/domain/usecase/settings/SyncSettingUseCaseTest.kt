package timur.gilfanov.messenger.domain.usecase.settings

import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncSettingRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SyncSettingUseCaseTest {
    private val session = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
    )
    private val logger = NoOpLogger()

    @Test
    fun `when sync succeeds then use case returns success`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session))
        val settingsRepository = SettingsRepositoryStub(
            syncSettingResult = ResultWithError.Success(Unit),
        )
        val useCase = SyncSettingUseCase(authRepository, settingsRepository, logger)

        val result = useCase(SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Success<Unit, *>>(result)
    }

    @Test
    fun `when identity not available then returns IdentityNotAvailable`() = runTest {
        val authRepository = AuthRepositoryFake()
        val settingsRepository = SettingsRepositoryStub(
            syncSettingResult = ResultWithError.Success(Unit),
        )
        val useCase = SyncSettingUseCase(authRepository, settingsRepository, logger)

        val result = useCase(SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingError>>(result)
        assertIs<SyncSettingError.IdentityNotAvailable>(result.error)
    }

    @Test
    fun `when setting not found then returns SettingNotFound`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session))
        val settingsRepository = SettingsRepositoryStub(
            syncSettingResult = ResultWithError.Failure(
                SyncSettingRepositoryError.SettingNotFound,
            ),
        )
        val useCase = SyncSettingUseCase(authRepository, settingsRepository, logger)

        val result = useCase(SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingError>>(result)
        assertIs<SyncSettingError.SettingNotFound>(result.error)
    }

    @Test
    fun `when remote sync fails then returns RemoteSyncFailed`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session))
        val settingsRepository = SettingsRepositoryStub(
            syncSettingResult = ResultWithError.Failure(
                SyncSettingRepositoryError.RemoteSyncFailed(
                    RemoteError.Failed.NetworkNotAvailable,
                ),
            ),
        )
        val useCase = SyncSettingUseCase(authRepository, settingsRepository, logger)

        val result = useCase(SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingError>>(result)
        assertIs<SyncSettingError.RemoteSyncFailed>(result.error)
        assertIs<RemoteError.Failed.NetworkNotAvailable>(result.error.error)
    }
}
