package timur.gilfanov.messenger.domain.usecase.settings

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ObserveAndApplyLocaleUseCaseTest {

    private val testSession = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
    )
    private val logger = NoOpLogger()

    @Test
    fun `applies locale when language is emitted`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.German)),
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            authRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCaseImpl(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()
        }

        assertEquals(listOf(UiLanguage.German), localeRepository.appliedLocales.toList())
    }

    @Test
    fun `applies multiple locale changes`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
                emit(Success(Settings(UiLanguage.German)))
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            authRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCaseImpl(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()
            awaitItem()
            awaitItem()
        }

        assertEquals(
            listOf(UiLanguage.English, UiLanguage.German, UiLanguage.English),
            localeRepository.appliedLocales.toList(),
        )
    }

    @Test
    fun `does not skip duplicate language emissions`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
                emit(Success(Settings(UiLanguage.English)))
                emit(Success(Settings(UiLanguage.German)))
            },
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            authRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCaseImpl(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()
            awaitItem()
            awaitItem()
        }

        assertEquals(
            listOf(UiLanguage.English, UiLanguage.English, UiLanguage.German),
            localeRepository.appliedLocales.toList(),
        )
    }

    @Test
    fun `does not apply locale when unauthorized`() = runTest {
        val authRepository = AuthRepositoryFake()
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.English)),
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            authRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCaseImpl(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()
        }

        assertEquals(emptyList<UiLanguage>(), localeRepository.appliedLocales.toList())
    }

    @Test
    fun `continues after error and applies next locale`() = runTest {
        val authRepository = AuthRepositoryFake()
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.German)),
        )
        val localeRepository = LocaleRepositoryStub()
        val observeUiLanguage = ObserveUiLanguageUseCase(
            authRepository,
            settingsRepository,
            logger,
        )
        val useCase = ObserveAndApplyLocaleUseCaseImpl(observeUiLanguage, localeRepository, logger)

        useCase().test {
            awaitItem()

            authRepository.setState(AuthState.Authenticated(testSession))

            awaitItem()
        }

        assertEquals(listOf(UiLanguage.German), localeRepository.appliedLocales.toList())
    }
}
