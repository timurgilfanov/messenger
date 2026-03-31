package timur.gilfanov.messenger.domain.usecase.settings

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncAllSettingsRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SyncAllPendingSettingsUseCaseTest {
    private val session = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
    )
    private val logger = NoOpLogger()

    @Test
    fun `when sync succeeds then use case returns success`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session))
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Success(Unit),
        )
        val useCase = SyncAllPendingSettingsUseCase(authRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Success<Unit, *>>(result)
    }

    @Test
    fun `when identity not available then returns IdentityNotAvailable`() = runTest {
        val authRepository = AuthRepositoryFake()
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Success(Unit),
        )
        val useCase = SyncAllPendingSettingsUseCase(authRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Failure<Unit, SyncAllPendingSettingsError>>(result)
        assertIs<SyncAllPendingSettingsError.IdentityNotAvailable>(result.error)
    }

    @Test
    fun `when remote sync fails then returns RemoteSyncFailed`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session))
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Failure(
                SyncAllSettingsRepositoryError.RemoteSyncFailed(
                    RemoteError.Failed.ServiceDown,
                ),
            ),
        )
        val useCase = SyncAllPendingSettingsUseCase(authRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Failure<Unit, SyncAllPendingSettingsError>>(result)
        assertIs<SyncAllPendingSettingsError.RemoteSyncFailed>(result.error)
        assertIs<RemoteError.Failed.ServiceDown>(result.error.error)
    }

    @Test
    fun `when local storage fails then returns LocalOperationFailed`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session))
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Failure(
                SyncAllSettingsRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )
        val useCase = SyncAllPendingSettingsUseCase(authRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Failure<Unit, SyncAllPendingSettingsError>>(result)
        assertIs<SyncAllPendingSettingsError.LocalOperationFailed>(result.error)
        assertIs<LocalStorageError.StorageFull>(result.error.error)
    }
}
