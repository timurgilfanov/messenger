package timur.gilfanov.messenger.domain.usecase.settings

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.DeviceId
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.profile.GetIdentityError
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ObserveSettingsUseCaseImplTest {

    private val testUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    private val testDeviceId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private fun createTestIdentity(): Identity = Identity(
        userId = UserId(testUserId),
        deviceId = DeviceId(testDeviceId),
    )

    private fun createTestSettings(language: UiLanguage = UiLanguage.English): Settings = Settings(
        uiLanguage = language,
    )

    @Test
    fun `emits settings successfully when identity resolves`() = runTest {
        val identity = createTestIdentity()
        val settings = createTestSettings(UiLanguage.German)

        val identityRepository = IdentityRepositoryStub(ResultWithError.Success(identity))
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Success(settings),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            identityRepository = identityRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Settings, ObserveSettingsError>>(result)
            assertEquals(settings, result.data)
            awaitComplete()
        }
    }

    @Test
    fun `emits Unauthorized error when identity fails`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            ResultWithError.Failure(GetIdentityError),
        )
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Success(createTestSettings()),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            identityRepository = identityRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Settings, ObserveSettingsError>>(result)
            assertEquals(ObserveSettingsError.Unauthorized, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `emits ObserveSettingsRepository error when repository fails`() = runTest {
        val identity = createTestIdentity()
        val repositoryError = GetSettingsRepositoryError.UnknownError(Exception("Test error"))

        val identityRepository = IdentityRepositoryStub(ResultWithError.Success(identity))
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Failure(repositoryError),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            identityRepository = identityRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Settings, ObserveSettingsError>>(result)
            val error = result.error
            assertIs<ObserveSettingsError.ObserveSettingsRepository>(error)
            assertEquals(repositoryError, error.error)
            awaitComplete()
        }
    }

    @Test
    fun `re-emits settings when identity changes`() = runTest {
        val identity1 = createTestIdentity()
        val identity2 = Identity(
            userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002")),
            deviceId = DeviceId(testDeviceId),
        )

        val identityFlow = MutableStateFlow<ResultWithError<Identity, GetIdentityError>>(
            ResultWithError.Success(identity1),
        )

        val identityRepository = IdentityRepositoryStub(identityFlow = identityFlow)
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            identityRepository = identityRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            val value1 = awaitItem()
            assertIs<ResultWithError.Success<Settings, ObserveSettingsError>>(value1)

            identityFlow.update { ResultWithError.Success(identity2) }

            val value2 = awaitItem()
            assertIs<ResultWithError.Success<Settings, ObserveSettingsError>>(value2)
        }
    }
}
