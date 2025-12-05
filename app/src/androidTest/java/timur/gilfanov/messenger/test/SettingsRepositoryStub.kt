package timur.gilfanov.messenger.test

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.usecase.user.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncSettingRepositoryError

class SettingsRepositoryStub : SettingsRepository {
    override fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        flowOf(ResultWithError.Success(Settings(uiLanguage = UiLanguage.English)))

    override fun observeConflicts(): Flow<SettingsConflictEvent> = emptyFlow()

    override suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = ResultWithError.Success(Unit)

    override suspend fun syncSetting(
        identity: Identity,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError> = ResultWithError.Success(Unit)

    override suspend fun syncAllPendingSettings(
        identity: Identity,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError> = ResultWithError.Success(Unit)
}
