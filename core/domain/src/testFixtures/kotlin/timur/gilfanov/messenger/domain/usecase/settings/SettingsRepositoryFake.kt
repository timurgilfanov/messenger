package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage
import timur.gilfanov.messenger.domain.usecase.settings.repository.ChangeLanguageRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.DeleteUserDataRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncSettingRepositoryError

class SettingsRepositoryFake(
    initialSettings: Settings,
    private val defaultSettings: Settings = initialSettings,
    private var changeResult: ResultWithError<Unit, ChangeLanguageRepositoryError> =
        ResultWithError.Success(Unit),
    private var deleteUserDataResult: ResultWithError<Unit, DeleteUserDataRepositoryError> =
        ResultWithError.Success(Unit),
) : SettingsRepository {

    var lastDeleteUserDataKey: UserScopeKey? = null
        private set
    var deleteUserDataCallCount: Int = 0
        private set

    private val settingsFlow =
        MutableStateFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            ResultWithError.Success(initialSettings),
        )

    override fun observeSettings(
        userKey: UserScopeKey,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>> = settingsFlow

    override fun observeConflicts(): Flow<SettingsConflictEvent> {
        error("Not implemented for this test")
    }

    override suspend fun changeUiLanguage(
        userKey: UserScopeKey,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError> {
        if (changeResult is ResultWithError.Success) {
            settingsFlow.update { current ->
                (current as? ResultWithError.Success)?.let {
                    ResultWithError.Success(it.data.copy(uiLanguage = language))
                } ?: current
            }
        }
        return changeResult
    }

    override suspend fun syncSetting(
        userKey: UserScopeKey,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError> =
        error("syncSetting not configured for this test")

    override suspend fun syncAllPendingSettings(
        userKey: UserScopeKey,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError> =
        error("syncAllPendingSettings not configured for this test")

    override suspend fun deleteUserData(
        userKey: UserScopeKey,
    ): ResultWithError<Unit, DeleteUserDataRepositoryError> {
        lastDeleteUserDataKey = userKey
        deleteUserDataCallCount++
        if (deleteUserDataResult is ResultWithError.Success) {
            settingsFlow.update {
                ResultWithError.Failure(GetSettingsRepositoryError.SettingsUnspecified)
            }
            yield()
            settingsFlow.update {
                ResultWithError.Success(defaultSettings)
            }
        }
        return deleteUserDataResult
    }
}
