package timur.gilfanov.messenger.test

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
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
        session: AuthSession,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> =
        flowOf(ResultWithError.Success(Settings(uiLanguage = UiLanguage.English)))

    override fun observeConflicts(): Flow<SettingsConflictEvent> = emptyFlow()

    override suspend fun changeUiLanguage(
        session: AuthSession,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> = ResultWithError.Success(Unit)

    override suspend fun syncSetting(
        session: AuthSession,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError> = ResultWithError.Success(Unit)

    override suspend fun syncAllPendingSettings(
        session: AuthSession,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError> = ResultWithError.Success(Unit)
}
