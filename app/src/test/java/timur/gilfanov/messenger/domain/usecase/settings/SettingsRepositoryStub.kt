package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.profile.Identity
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncSettingRepositoryError

class SettingsRepositoryStub(
    private val settingsFlow: Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        emptyFlow(),
    private val changeLanguage: ResultWithError<Unit, ChangeLanguageRepositoryError> =
        ResultWithError.Success(Unit),
    private val syncSettingResult: ResultWithError<Unit, SyncSettingRepositoryError>? = null,
    private val syncAllResult: ResultWithError<Unit, SyncAllSettingsRepositoryError>? = null,
) : SettingsRepository {

    constructor(
        settings: ResultWithError<Settings, GetSettingsRepositoryError>,
        changeLanguageResult: ResultWithError<Unit, ChangeLanguageRepositoryError> =
            ResultWithError.Success(Unit),
        syncSettingResult: ResultWithError<Unit, SyncSettingRepositoryError>? = null,
        syncAllResult: ResultWithError<Unit, SyncAllSettingsRepositoryError>? = null,
    ) : this(
        settingsFlow = flowOf(settings),
        changeLanguage = changeLanguageResult,
        syncSettingResult = syncSettingResult,
        syncAllResult = syncAllResult,
    )

    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> = settingsFlow

    override fun observeConflicts(): Flow<SettingsConflictEvent> {
        error("Not implemented for this test")
    }

    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = changeLanguage

    override suspend fun syncSetting(
        identity: Identity,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError> =
        syncSettingResult ?: error("syncSetting not configured for this test")

    override suspend fun syncAllPendingSettings(
        identity: Identity,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError> =
        syncAllResult ?: error("syncAllPendingSettings not configured for this test")
}
