package timur.gilfanov.messenger.ui.screen.settings

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthProvider
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.settings.ChangeUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.ObserveUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryFake
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository

object LanguageViewModelTestFixtures {

    private val TEST_SESSION = AuthSession(
        tokens = AuthTokens(accessToken = "test-access", refreshToken = "test-refresh"),
        provider = AuthProvider.EMAIL,
    )

    fun createTestSettings(language: UiLanguage = UiLanguage.English): Settings = Settings(
        uiLanguage = language,
    )

    fun createViewModel(
        authRepository: AuthRepository,
        settingsRepository: SettingsRepository,
    ): LanguageViewModel {
        val observeUseCase = ObserveUiLanguageUseCase(
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )
        val changeUseCase = ChangeUiLanguageUseCase(
            authRepository = authRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )
        return LanguageViewModel(
            observe = observeUseCase,
            change = changeUseCase,
            logger = NoOpLogger(),
        )
    }

    fun createSuccessfulIdentityRepository(): AuthRepositoryFake = AuthRepositoryFake(TEST_SESSION)

    fun createFailingIdentityRepository(): AuthRepositoryFake = AuthRepositoryFake()

    fun createSettingsRepositoryWithLanguage(language: UiLanguage): SettingsRepositoryStub =
        SettingsRepositoryStub(
            settings = ResultWithError.Success(createTestSettings(language)),
        )

    fun createSettingsRepositoryWithFlow(
        settingsFlow: Flow<ResultWithError<Settings, GetSettingsRepositoryError>>,
    ): SettingsRepositoryStub = SettingsRepositoryStub(settingsFlow = settingsFlow)

    fun createSettingsRepositoryWithChangeError(
        currentLanguage: UiLanguage = UiLanguage.English,
        changeError: ChangeLanguageRepositoryError,
    ): SettingsRepositoryStub = SettingsRepositoryStub(
        settings = ResultWithError.Success(createTestSettings(currentLanguage)),
        changeLanguageResult = ResultWithError.Failure(changeError),
    )

    fun createSettingsRepositoryFake(
        currentLanguage: UiLanguage = UiLanguage.English,
        changeResult: ResultWithError<Unit, ChangeLanguageRepositoryError> =
            ResultWithError.Success(Unit),
    ): SettingsRepositoryFake = SettingsRepositoryFake(
        initialSettings = createTestSettings(currentLanguage),
        changeResult = changeResult,
    )
}
