package timur.gilfanov.messenger.domain.usecase.user

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ChangeUiLanguageUseCaseTest {
    private val identity = Identity(
        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        deviceId = DeviceId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        ),
    )
    private val identityRepository: IdentityRepository = IdentityRepositoryStub(
        flowOf(
            Success(
                identity,
            ),
        ),
    )

    @Test
    fun `when repository succeed then use case succeed`() = runTest {
        val settingsRepository = SettingsRepositoryStub(changeLanguage = Success(Unit))
        val useCase = ChangeUiLanguageUseCase(identityRepository, settingsRepository)
        val result = useCase(UiLanguage.English)
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `when repository failed then use case failed`() = runTest {
        val settingsRepository = SettingsRepositoryStub(
            changeLanguage = Failure(
                ChangeLanguageRepositoryError.Transient,
            ),
        )
        val useCase = ChangeUiLanguageUseCase(identityRepository, settingsRepository)
        val result = useCase(UiLanguage.English)
        assertIs<Failure<*, ChangeUiLanguageError>>(result)
        assertIs<ChangeUiLanguageError.ChangeLanguageRepository>(result.error)
        assertIs<ChangeLanguageRepositoryError.Transient>(result.error.error)
    }

    @Test
    fun `when identity repository fails then use case returns unauthorized`() = runTest {
        val failingIdentityRepository = IdentityRepositoryStub(
            flowOf(
                Failure(Unit),
            ),
        )
        val settingsRepository = SettingsRepositoryStub(changeLanguage = Success(Unit))
        val useCase = ChangeUiLanguageUseCase(failingIdentityRepository, settingsRepository)

        val result = useCase(UiLanguage.German)

        assertIs<Failure<*, ChangeUiLanguageError>>(result)
        assertIs<ChangeUiLanguageError.Unauthorized>(result.error)
    }
}
