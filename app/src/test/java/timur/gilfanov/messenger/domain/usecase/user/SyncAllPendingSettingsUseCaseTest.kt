package timur.gilfanov.messenger.domain.usecase.user

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncAllSettingsRepositoryError

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
    fun `when remote sync fails then returns SyncFailed with RemoteSyncFailed`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityFlow = flowOf(ResultWithError.Success(identity)),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Failure(
                SyncAllSettingsRepositoryError.RemoteSyncFailed(
                    RepositoryError.Failed.ServiceDown,
                ),
            ),
        )
        val useCase = SyncAllPendingSettingsUseCase(identityRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Failure<Unit, SyncAllPendingSettingsError>>(result)
        assertIs<SyncAllPendingSettingsError.SyncFailed>(result.error)
        assertIs<SyncAllSettingsRepositoryError.RemoteSyncFailed>(result.error.error)
        assertIs<RepositoryError.Failed.ServiceDown>(result.error.error.error)
    }

    @Test
    fun `when local storage fails then returns SyncFailed with LocalStorageError`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityFlow = flowOf(ResultWithError.Success(identity)),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncAllResult = ResultWithError.Failure(
                SyncAllSettingsRepositoryError.LocalStorageError.StorageFull,
            ),
        )
        val useCase = SyncAllPendingSettingsUseCase(identityRepository, settingsRepository, logger)

        val result = useCase()

        assertIs<ResultWithError.Failure<Unit, SyncAllPendingSettingsError>>(result)
        assertIs<SyncAllPendingSettingsError.SyncFailed>(result.error)
        assertIs<SyncAllSettingsRepositoryError.LocalStorageError.StorageFull>(result.error.error)
    }
}
