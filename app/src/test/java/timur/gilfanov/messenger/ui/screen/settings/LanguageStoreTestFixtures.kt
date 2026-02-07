package timur.gilfanov.messenger.ui.screen.settings

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.DeviceId
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.profile.GetIdentityError
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.ChangeUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.ObserveUiLanguageUseCase
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryFake
import timur.gilfanov.messenger.domain.usecase.settings.SettingsRepositoryStub
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository

object LanguageStoreTestFixtures {

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
        settingsRepository: SettingsRepository,
    ): LanguageStore {
        val observeUseCase = ObserveUiLanguageUseCase(
            identityRepository = identityRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )
        val changeUseCase = ChangeUiLanguageUseCase(
            identityRepository = identityRepository,
            settingsRepository = settingsRepository,
            logger = NoOpLogger(),
        )
        return LanguageStore(
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

    fun createSettingsRepositoryFake(
        currentLanguage: UiLanguage = UiLanguage.English,
        changeResult: ResultWithError<Unit, ChangeLanguageRepositoryError> =
            ResultWithError.Success(Unit),
    ): SettingsRepositoryFake = SettingsRepositoryFake(
        initialSettings = createTestSettings(currentLanguage),
        changeResult = changeResult,
    )
}
