package timur.gilfanov.messenger.domain.usecase.settings

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.DeviceId
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.profile.GetIdentityError
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncAllSettingsRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SyncAllPendingSettingsUseCaseTest {
    private val identity = Identity(
        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        deviceId = DeviceId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
    )
    private val logger = NoOpLogger()

    @Test
    fun `when sync succeeds then use case returns success`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityFlow = flowOf(ResultWithError.Success(identity)),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Success(Unit),
        )
        val useCase = SyncAllPendingSettingsUseCase(identityRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Success<Unit, *>>(result)
    }

    @Test
    fun `when identity not available then returns IdentityNotAvailable`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityFlow = flowOf(ResultWithError.Failure(GetIdentityError)),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Success(Unit),
        )
        val useCase = SyncAllPendingSettingsUseCase(identityRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Failure<Unit, SyncAllPendingSettingsError>>(result)
        assertIs<SyncAllPendingSettingsError.IdentityNotAvailable>(result.error)
    }

    @Test
    fun `when remote sync fails then returns RemoteSyncFailed`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityFlow = flowOf(ResultWithError.Success(identity)),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Failure(
                SyncAllSettingsRepositoryError.RemoteSyncFailed(
                    RemoteError.Failed.ServiceDown,
                ),
            ),
        )
        val useCase = SyncAllPendingSettingsUseCase(identityRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Failure<Unit, SyncAllPendingSettingsError>>(result)
        assertIs<SyncAllPendingSettingsError.RemoteSyncFailed>(result.error)
        assertIs<RemoteError.Failed.ServiceDown>(result.error.error)
    }

    @Test
    fun `when local storage fails then returns LocalOperationFailed`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityFlow = flowOf(ResultWithError.Success(identity)),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Failure(
                SyncAllSettingsRepositoryError.LocalOperationFailed(LocalStorageError.StorageFull),
            ),
        )
        val useCase = SyncAllPendingSettingsUseCase(identityRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Failure<Unit, SyncAllPendingSettingsError>>(result)
        assertIs<SyncAllPendingSettingsError.LocalOperationFailed>(result.error)
        assertIs<LocalStorageError.StorageFull>(result.error.error)
    }
}
