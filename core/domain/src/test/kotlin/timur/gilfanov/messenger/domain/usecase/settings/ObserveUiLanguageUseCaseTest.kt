package timur.gilfanov.messenger.domain.usecase.settings

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
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
class ObserveUiLanguageUseCaseTest {

    private val testSession = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
    )
    private val logger = NoOpLogger()

    @Test
    fun `emits UI language from settings`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.English)),
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            val result = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(result)
            assertEquals(UiLanguage.English, result.data)
        }
    }

    @Test
    fun `emits multiple UI language updates`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
                emit(Success(Settings(UiLanguage.German)))
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            val first = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(first)
            assertEquals(UiLanguage.English, first.data)

            val second = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(second)
            assertEquals(UiLanguage.German, second.data)

            val third = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(third)
            assertEquals(UiLanguage.English, third.data)
        }
    }

    @Test
    fun `emits unauthorized when identity repository fails`() = runTest {
        val authRepository = AuthRepositoryFake()
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(result)
            assertIs<ObserveUiLanguageError.Unauthorized>(result.error)
        }
    }

    @Test
    fun `continue flow when identity unauthorized recover`() = runTest {
        val authRepository = AuthRepositoryFake()
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            val firstResult = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(firstResult)
            assertIs<ObserveUiLanguageError.Unauthorized>(firstResult.error)

            authRepository.setState(AuthState.Authenticated(testSession))

            val secondResult = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(secondResult)
            assertEquals(UiLanguage.English, secondResult.data)
        }
    }

    @Test
    fun `emits SettingsResetToDefaults error when settings reset to defaults`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Failure(GetSettingsRepositoryError.SettingsResetToDefaults))
            },
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(result)
            assertIs<ObserveUiLanguageError.SettingsResetToDefaults>(result.error)
        }
    }

    @Test
    fun `emits LocalOperationFailed error on temporary errors`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(
                    Failure(
                        GetSettingsRepositoryError.LocalOperationFailed(
                            LocalStorageError.TemporarilyUnavailable,
                        ),
                    ),
                )
            },
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(result)
            assertIs<ObserveUiLanguageError.LocalOperationFailed>(result.error)
            assertIs<LocalStorageError.TemporarilyUnavailable>(result.error.error)
        }
    }

    @Test
    fun `handles mixed success and errors in flow`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
                emit(
                    Failure(
                        GetSettingsRepositoryError.LocalOperationFailed(
                            LocalStorageError.TemporarilyUnavailable,
                        ),
                    ),
                )
                emit(Success(Settings(UiLanguage.German)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            val first = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(first)
            assertEquals(UiLanguage.English, first.data)

            val second = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(second)
            assertIs<ObserveUiLanguageError.LocalOperationFailed>(second.error)

            val third = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(third)
            assertEquals(UiLanguage.German, third.data)
        }
    }

    @Test
    fun `handles multiple repository errors and recovery in flow`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Authenticated(testSession))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(
                    Failure(
                        GetSettingsRepositoryError.LocalOperationFailed(
                            LocalStorageError.TemporarilyUnavailable,
                        ),
                    ),
                )
                emit(Failure(GetSettingsRepositoryError.SettingsResetToDefaults))
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            val first = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(first)
            assertIs<ObserveUiLanguageError.LocalOperationFailed>(first.error)
            assertIs<LocalStorageError.TemporarilyUnavailable>(first.error.error)

            val second = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(second)
            assertIs<ObserveUiLanguageError.SettingsResetToDefaults>(second.error)

            val third = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(third)
            assertEquals(UiLanguage.English, third.data)
        }
    }

    @Test
    fun `emits nothing while auth state is Loading`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Loading)
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.English)),
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            expectNoEvents()
        }
    }

    @Test
    fun `emits language after Loading transitions to Authenticated`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Loading)
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.German)),
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            expectNoEvents()

            authRepository.setState(AuthState.Authenticated(testSession))

            val result = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(result)
            assertEquals(UiLanguage.German, result.data)
        }
    }

    @Test
    fun `emits Unauthorized after Loading transitions to Unauthenticated`() = runTest {
        val authRepository = AuthRepositoryFake(AuthState.Loading)
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.English)),
        )
        val useCase = ObserveUiLanguageUseCase(authRepository, settingsRepository, logger)

        useCase().test {
            expectNoEvents()

            authRepository.setState(AuthState.Unauthenticated)

            val result = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(result)
            assertIs<ObserveUiLanguageError.Unauthorized>(result.error)
        }
    }
}