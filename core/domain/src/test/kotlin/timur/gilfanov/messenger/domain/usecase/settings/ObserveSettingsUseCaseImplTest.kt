package timur.gilfanov.messenger.domain.usecase.settings

import app.cash.turbine.test
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ObserveSettingsUseCaseImplTest {

    private fun createTestSession(): AuthSession = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
    )

    private fun createTestSettings(language: UiLanguage = UiLanguage.English): Settings = Settings(
        uiLanguage = language,
    )

    @Test
    fun `emits settings successfully when identity resolves`() = runTest {
        val session = createTestSession()
        val settings = createTestSettings(UiLanguage.German)

        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session))
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Success(settings),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Settings, ObserveSettingsError>>(result)
            assertEquals(settings, result.data)
        }
    }

    @Test
    fun `emits Unauthorized error when identity fails`() = runTest {
        val authRepository = AuthRepositoryFake()
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Success(createTestSettings()),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Settings, ObserveSettingsError>>(result)
            assertEquals(ObserveSettingsError.Unauthorized, result.error)
        }
    }

    @Test
    fun `emits LocalOperationFailed error when repository fails`() = runTest {
        val session = createTestSession()
        val localStorageError = LocalStorageError.UnknownError(Exception("Test error"))

        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session))
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Failure(
                GetSettingsRepositoryError.LocalOperationFailed(localStorageError),
            ),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Settings, ObserveSettingsError>>(result)
            val error = result.error
            assertIs<ObserveSettingsError.LocalOperationFailed>(error)
            assertEquals(localStorageError, error.error)
        }
    }

    @Test
    fun `re-emits settings when auth state changes`() = runTest {
        val session1 = createTestSession()
        val session2 = AuthSession(
            tokens = AuthTokens(accessToken = "test-access-2", refreshToken = "test-refresh-2"),
            provider = AuthProvider.EMAIL,
        )

        val authRepository = AuthRepositoryFake(AuthState.Authenticated(session1))
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Success(createTestSettings(UiLanguage.English)),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            val value1 = awaitItem()
            assertIs<ResultWithError.Success<Settings, ObserveSettingsError>>(value1)

            authRepository.setState(AuthState.Authenticated(session2))

            val value2 = awaitItem()
            assertIs<ResultWithError.Success<Settings, ObserveSettingsError>>(value2)
        }
    }

    @Test
    fun `emits nothing while auth state is Loading`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Loading)
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Success(createTestSettings()),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            expectNoEvents()
        }
    }

    @Test
    fun `emits settings after Loading transitions to Authenticated`() = runTest {
        val session = createTestSession()
        val settings = createTestSettings(UiLanguage.German)

        val authRepository = AuthRepositoryFake(AuthState.Loading)
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Success(settings),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            expectNoEvents()

            authRepository.setState(AuthState.Authenticated(session))

            val result = awaitItem()
            assertIs<ResultWithError.Success<Settings, ObserveSettingsError>>(result)
            assertEquals(settings, result.data)
        }
    }

    @Test
    fun `emits Unauthorized after Loading transitions to Unauthenticated`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Loading)
        val settingsRepository = SettingsRepositoryStub(
            settings = ResultWithError.Success(createTestSettings()),
        )

        val useCase = ObserveSettingsUseCaseImpl(
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )

        useCase().test {
            expectNoEvents()

            authRepository.setState(AuthState.Unauthenticated)

            val result = awaitItem()
            assertIs<ResultWithError.Failure<Settings, ObserveSettingsError>>(result)
            assertEquals(ObserveSettingsError.Unauthorized, result.error)
        }
    }
}