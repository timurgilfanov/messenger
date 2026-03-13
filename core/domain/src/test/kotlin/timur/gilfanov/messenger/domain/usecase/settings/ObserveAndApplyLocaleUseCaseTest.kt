package timur.gilfanov.messenger.domain.usecase.settings

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.profile.DeviceId
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.profile.GetIdentityError
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepositoryStub

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ObserveAndApplyLocaleUseCaseTest {

    private val testIdentity = Identity(
        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        deviceId = DeviceId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
    )
    private val logger = NoOpLogger()

    @Test
    fun `applies locale when language is emitted`() = runTest {
        val identityRepository = IdentityRepositoryStub(Success(testIdentity))
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.German)),
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            identityRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCase(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()
            awaitComplete()
        }

        assertEquals(listOf(UiLanguage.German), localeRepository.appliedLocales.toList())
    }

    @Test
    fun `applies multiple locale changes`() = runTest {
        val identityRepository = IdentityRepositoryStub(Success(testIdentity))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
                emit(Success(Settings(UiLanguage.German)))
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            identityRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCase(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        assertEquals(
            listOf(UiLanguage.English, UiLanguage.German, UiLanguage.English),
            localeRepository.appliedLocales.toList(),
        )
    }

    @Test
    fun `does not skip duplicate language emissions`() = runTest {
        val identityRepository = IdentityRepositoryStub(Success(testIdentity))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
                emit(Success(Settings(UiLanguage.English)))
                emit(Success(Settings(UiLanguage.German)))
            },
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            identityRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCase(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        assertEquals(
            listOf(UiLanguage.English, UiLanguage.English, UiLanguage.German),
            localeRepository.appliedLocales.toList(),
        )
    }

    @Test
    fun `does not apply locale when unauthorized`() = runTest {
        val identityRepository = IdentityRepositoryStub(Failure(GetIdentityError))
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.English)),
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            identityRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCase(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()
            awaitComplete()
        }

        assertEquals(emptyList<UiLanguage>(), localeRepository.appliedLocales.toList())
    }

    @Test
    fun `continues after error and applies next locale`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            flow {
                emit(Failure(GetIdentityError))
                emit(Success(testIdentity))
            },
        )
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.German)),
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            identityRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCase(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        assertEquals(listOf(UiLanguage.German), localeRepository.appliedLocales.toList())
    }
}
