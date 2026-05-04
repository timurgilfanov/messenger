package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _settingsFlow =
        MutableSharedFlow<ResultWithError<Settings, GetSettingsRepositoryError>>(
            replay = 1,
            extraBufferCapacity = 1,
        ).also { flow ->
            flow.tryEmit(ResultWithError.Success(initialSettings))
        }

    private val settingsFlow = _settingsFlow.asSharedFlow()

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
            val current = _settingsFlow.replayCache.lastOrNull()
            if (current is ResultWithError.Success) {
                _settingsFlow.emit(
                    ResultWithError.Success(current.data.copy(uiLanguage = language)),
                )
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
            _settingsFlow.emit(
                ResultWithError.Failure(GetSettingsRepositoryError.SettingsUnspecified),
            )
            _settingsFlow.emit(ResultWithError.Success(defaultSettings))
        }
        return deleteUserDataResult
    }
}
