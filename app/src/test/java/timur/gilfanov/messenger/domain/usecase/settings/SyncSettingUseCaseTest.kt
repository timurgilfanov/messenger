package timur.gilfanov.messenger.domain.usecase.settings

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.DeviceId
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.profile.GetIdentityError
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncSettingRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class SyncSettingUseCaseTest {
    private val userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
    private val identity = Identity(
        userId = userId,
        deviceId = DeviceId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
    )
    private val logger = NoOpLogger()

    @Test
    fun `when sync succeeds then use case returns success`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityResult = ResultWithError.Success(identity),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncSettingResult = ResultWithError.Success(Unit),
        )
        val useCase = SyncSettingUseCase(identityRepository, settingsRepository, logger)

        val result = useCase(userId, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Success<Unit, *>>(result)
    }

    @Test
    fun `when identity not available then returns IdentityNotAvailable`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityResult = ResultWithError.Failure(GetIdentityError),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncSettingResult = ResultWithError.Success(Unit),
        )
        val useCase = SyncSettingUseCase(identityRepository, settingsRepository, logger)

        val result = useCase(userId, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingError>>(result)
        assertIs<SyncSettingError.IdentityNotAvailable>(result.error)
    }

    @Test
    fun `when setting not found then returns SyncFailed with SettingNotFound`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityResult = ResultWithError.Success(identity),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncSettingResult = ResultWithError.Failure(
                SyncSettingRepositoryError.SettingNotFound,
            ),
        )
        val useCase = SyncSettingUseCase(identityRepository, settingsRepository, logger)

        val result = useCase(userId, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingError>>(result)
        assertIs<SyncSettingError.SyncFailed>(result.error)
        assertIs<SyncSettingRepositoryError.SettingNotFound>(result.error.error)
    }

    @Test
    fun `when remote sync fails then returns SyncFailed with RemoteSyncFailed`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            identityResult = ResultWithError.Success(identity),
        )
        val settingsRepository = SettingsRepositoryStub(
            syncSettingResult = ResultWithError.Failure(
                SyncSettingRepositoryError.RemoteSyncFailed(
                    RemoteError.Failed.NetworkNotAvailable,
                ),
            ),
        )
        val useCase = SyncSettingUseCase(identityRepository, settingsRepository, logger)

        val result = useCase(userId, SettingKey.UI_LANGUAGE)

        assertIs<ResultWithError.Failure<Unit, SyncSettingError>>(result)
        assertIs<SyncSettingError.SyncFailed>(result.error)
        assertIs<SyncSettingRepositoryError.RemoteSyncFailed>(result.error.error)
        assertIs<RemoteError.Failed.NetworkNotAvailable>(result.error.error.error)
    }
}
