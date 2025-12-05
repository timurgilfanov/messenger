package timur.gilfanov.messenger.ui.screen.user

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.DeviceId
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.ChangeUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.user.GetIdentityError
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.user.IdentityRepositoryStub
import timur.gilfanov.messenger.domain.usecase.user.ObserveUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.user.SettingsRepositoryStub
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError

object LanguageViewModelTestFixtures {

    private val TEST_USER_ID = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
    private val TEST_DEVICE_ID = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002")

    fun createTestIdentity(): Identity = Identity(
        userId = UserId(TEST_USER_ID),
        deviceId = DeviceId(TEST_DEVICE_ID),
    )

    fun createTestSettings(language: UiLanguage = UiLanguage.English): Settings = Settings(
        uiLanguage = language,
    )

    fun createViewModel(
        identityRepository: IdentityRepository,
        settingsRepositoryStub: SettingsRepositoryStub,
    ): LanguageViewModel {
        val observeUseCase = ObserveUiLanguageUseCase(
            identityRepository = identityRepository,
            settingsRepository = settingsRepositoryStub,
            logger = NoOpLogger(),
        )
        val changeUseCase = ChangeUiLanguageUseCase(
            identityRepository = identityRepository,
            settingsRepository = settingsRepositoryStub,
            logger = NoOpLogger(),
        )
        return LanguageViewModel(
            observe = observeUseCase,
            change = changeUseCase,
            logger = NoOpLogger(),
        )
    }

    fun createSuccessfulIdentityRepository(
        identity: Identity = createTestIdentity(),
    ): IdentityRepository = IdentityRepositoryStub(ResultWithError.Success(identity))

    fun createFailingIdentityRepository(): IdentityRepository =
        IdentityRepositoryStub(ResultWithError.Failure(GetIdentityError))

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
}
