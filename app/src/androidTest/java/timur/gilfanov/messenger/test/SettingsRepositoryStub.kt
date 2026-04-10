package timur.gilfanov.messenger.test

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncSettingRepositoryError

class SettingsRepositoryStub : SettingsRepository {
    override fun observeSettings(
        userKey: UserScopeKey,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        flowOf(ResultWithError.Success(Settings(uiLanguage = UiLanguage.English)))

    override fun observeConflicts(): Flow<SettingsConflictEvent> = emptyFlow()

    override suspend fun changeUiLanguage(
        userKey: UserScopeKey,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = ResultWithError.Success(Unit)

    override suspend fun syncSetting(
        userKey: UserScopeKey,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError> = ResultWithError.Success(Unit)

    override suspend fun syncAllPendingSettings(
        userKey: UserScopeKey,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError> = ResultWithError.Success(Unit)
}
