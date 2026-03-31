package timur.gilfanov.messenger.domain.usecase.settings

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ChangeUiLanguageUseCaseTest {
    private val session = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
        userId = UserId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
    )
    private val authRepository: AuthRepositoryFake =
        AuthRepositoryFake(AuthState.Authenticated(session))
    private val logger = NoOpLogger()

    @Test
    fun `when repository succeed then use case succeed`() = runTest {
        val settingsRepository = SettingsRepositoryStub(changeLanguage = Success(Unit))
        val useCase = ChangeUiLanguageUseCase(authRepository, settingsRepository, logger)
        val result = useCase(UiLanguage.English)
        assertIs<Success<Unit, *>>(result)
    }

    @Test
    fun `when repository failed then use case failed`() = runTest {
        val settingsRepository = SettingsRepositoryStub(
            changeLanguage = Failure(
                ChangeLanguageRepositoryError.LocalOperationFailed(
                    LocalStorageError.TemporarilyUnavailable,
                ),
            ),
        )
        val useCase = ChangeUiLanguageUseCase(authRepository, settingsRepository, logger)
        val result = useCase(UiLanguage.English)
        assertIs<Failure<*, ChangeUiLanguageError>>(result)
        assertIs<ChangeUiLanguageError.LocalOperationFailed>(result.error)
        assertIs<LocalStorageError.TemporarilyUnavailable>(result.error.error)
    }

    @Test
    fun `when identity repository fails then use case returns unauthorized`() = runTest {
        val unauthenticatedRepository = AuthRepositoryFake()
        val settingsRepository = SettingsRepositoryStub(changeLanguage = Success(Unit))
        val useCase = ChangeUiLanguageUseCase(unauthenticatedRepository, settingsRepository, logger)

        val result = useCase(UiLanguage.German)

        assertIs<Failure<*, ChangeUiLanguageError>>(result)
        assertIs<ChangeUiLanguageError.Unauthorized>(result.error)
    }
}
