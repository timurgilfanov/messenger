package timur.gilfanov.messenger.domain.usecase.user

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.ApplyRemoteSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncLocalToRemoteRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ChangeUiLanguageUseCaseTest {
    private val identityRepository: IdentityRepository = object : IdentityRepository {
        override val identity: Flow<ResultWithError<Identity, GetIdentityError>> =
            flowOf(
                Success(
                    Identity(
                        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                        deviceId = DeviceId(
                            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                        ),
                    ),
                ),
            )
    }

    @Test
    fun `when repository succeed then use case succeed`() = runTest {
        val settingsRepository = SettingsRepositoryStub(Success(Unit))
        val useCase = ChangeUiLanguageUseCase(identityRepository, settingsRepository)
        val result = useCase(UiLanguage.English)
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `when repository failed then use case failed`() = runTest {
        val settingsRepository = SettingsRepositoryStub(
            Failure(ChangeLanguageRepositoryError.LanguageNotChanged(transient = false)),
        )
        val useCase = ChangeUiLanguageUseCase(identityRepository, settingsRepository)
        val result = useCase(UiLanguage.English)
        assertIs<Failure<*, ChangeUiLanguageError>>(result)
        assertIs<ChangeUiLanguageError.ChangeLanguageRepository>(result.error)
        assertIs<ChangeLanguageRepositoryError.LanguageNotChanged>(result.error.error)
    }
}

private class SettingsRepositoryStub(
    val changeLanguageResult: ResultWithError<Unit, ChangeLanguageRepositoryError>,
) : SettingsRepository {
    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> = emptyFlow()

    override suspend fun changeLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = changeLanguageResult

    override suspend fun applyRemoteSettings(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, ApplyRemoteSettingsRepositoryError> = Success(Unit)

    override suspend fun syncLocalToRemote(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, SyncLocalToRemoteRepositoryError> = Success(Unit)
}
