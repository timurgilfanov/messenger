package timur.gilfanov.messenger.domain.usecase.user

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ObserveUiLanguageUseCaseTest {

    private val testIdentity = Identity(
        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
        deviceId = DeviceId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
    )
    private val logger = NoOpLogger()

    @Test
    fun `emits UI language from settings`() = runTest {
        val identityRepository = IdentityRepositoryStub(Success(testIdentity))
        val settingsRepository = SettingsRepositoryStub(
            settings = Success(Settings(UiLanguage.English)),
        )
        val useCase = ObserveUiLanguageUseCase(identityRepository, settingsRepository, logger)

        useCase().test {
            val result = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(result)
            assertEquals(UiLanguage.English, result.data)
            awaitComplete()
        }
    }

    @Test
    fun `emits multiple UI language updates`() = runTest {
        val identityRepository = IdentityRepositoryStub(Success(testIdentity))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
                emit(Success(Settings(UiLanguage.German)))
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(identityRepository, settingsRepository, logger)

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

            awaitComplete()
        }
    }

    @Test
    fun `emits unauthorized when identity repository fails`() = runTest {
        val identityRepository = IdentityRepositoryStub(Failure(Unit))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(identityRepository, settingsRepository, logger)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(result)
            assertIs<ObserveUiLanguageError.Unauthorized>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `continue flow when identity unauthorized recover`() = runTest {
        val identityRepository = IdentityRepositoryStub(
            flow {
                emit(Failure(Unit))
                emit(Success(testIdentity))
            },
        )
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(identityRepository, settingsRepository, logger)

        useCase().test {
            val firstResult = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(firstResult)
            assertIs<ObserveUiLanguageError.Unauthorized>(firstResult.error)

            val secondResult = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(secondResult)
            assertEquals(UiLanguage.English, secondResult.data)

            awaitComplete()
        }
    }

    @Test
    fun `emits repository error when settings reset to defaults`() = runTest {
        val identityRepository = IdentityRepositoryStub(Success(testIdentity))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Failure(GetSettingsRepositoryError.SettingsResetToDefaults))
            },
        )
        val useCase = ObserveUiLanguageUseCase(identityRepository, settingsRepository, logger)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(result)
            assertIs<ObserveUiLanguageError.ObserveLanguageRepository>(result.error)
            assertIs<GetSettingsRepositoryError.SettingsResetToDefaults>(result.error.error)
            awaitComplete()
        }
    }

    @Test
    fun `emits repository error on temporary errors`() = runTest {
        val identityRepository = IdentityRepositoryStub(Success(testIdentity))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Failure(GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable))
            },
        )
        val useCase = ObserveUiLanguageUseCase(identityRepository, settingsRepository, logger)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(result)
            assertIs<ObserveUiLanguageError.ObserveLanguageRepository>(result.error)
            assertIs<GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable>(
                result.error.error,
            )
            awaitComplete()
        }
    }

    @Test
    fun `handles mixed success and errors in flow`() = runTest {
        val identityRepository = IdentityRepositoryStub(Success(testIdentity))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Success(Settings(UiLanguage.English)))
                emit(Failure(GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable))
                emit(Success(Settings(UiLanguage.German)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(identityRepository, settingsRepository, logger)

        useCase().test {
            val first = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(first)
            assertEquals(UiLanguage.English, first.data)

            val second = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(second)
            assertIs<ObserveUiLanguageError.ObserveLanguageRepository>(second.error)

            val third = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(third)
            assertEquals(UiLanguage.German, third.data)

            awaitComplete()
        }
    }

    @Test
    fun `handles multiple repository errors and recovery in flow`() = runTest {
        val identityRepository = IdentityRepositoryStub(Success(testIdentity))
        val settingsRepository = SettingsRepositoryStub(
            settingsFlow = flow {
                emit(Failure(GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable))
                emit(Failure(GetSettingsRepositoryError.SettingsResetToDefaults))
                emit(Success(Settings(UiLanguage.English)))
            },
        )
        val useCase = ObserveUiLanguageUseCase(identityRepository, settingsRepository, logger)

        useCase().test {
            val first = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(first)
            assertIs<ObserveUiLanguageError.ObserveLanguageRepository>(first.error)
            assertIs<GetSettingsRepositoryError.Recoverable.TemporarilyUnavailable>(
                first.error.error,
            )

            val second = awaitItem()
            assertIs<Failure<UiLanguage, ObserveUiLanguageError>>(second)
            assertIs<ObserveUiLanguageError.ObserveLanguageRepository>(second.error)
            assertIs<GetSettingsRepositoryError.SettingsResetToDefaults>(second.error.error)

            val third = awaitItem()
            assertIs<Success<UiLanguage, ObserveUiLanguageError>>(third)
            assertEquals(UiLanguage.English, third.data)

            awaitComplete()
        }
    }
}
