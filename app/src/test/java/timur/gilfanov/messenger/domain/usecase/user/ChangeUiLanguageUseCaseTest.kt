package timur.gilfanov.messenger.domain.usecase.user

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.UserRepositoryError

@Category(Unit::class)
class ChangeUiLanguageUseCaseTest {
    val userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))

    @Test
    fun `when repository succeed then use case succeed`() = runTest {
        val settingsRepository = SettingsRepositoryStub(Success(Unit))
        val useCase = ChangeUiLanguageUseCase(settingsRepository)
        val result = useCase(userId, UiLanguage.English)
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `when repository failed then use case failed`() = runTest {
        val settingsRepository = SettingsRepositoryStub(
            Failure(ChangeLanguageRepositoryError.LanguageNotChangedForAllDevices),
        )
        val useCase = ChangeUiLanguageUseCase(settingsRepository)
        val result = useCase(userId, UiLanguage.English)
        assertIs<Failure<*, ChangeUiLanguageError>>(result)
        assertIs<ChangeLanguageRepositoryError.LanguageNotChangedForAllDevices>(result.error)
    }
}

private class SettingsRepositoryStub(
    val changeLanguageResult: ResultWithError<Unit, ChangeLanguageRepositoryError>,
) : SettingsRepository {
    override fun observeSettings(
        userId: UserId,
    ): Flow<ResultWithError<Settings, UserRepositoryError>> = emptyFlow()

    override suspend fun changeLanguage(
        userId: UserId,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = changeLanguageResult
}
