package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Remote data source for user settings data.
 *
 * Provides access to settings data and operations on the backend service.
 * Handles network communication for settings synchronization across devices.
 */
interface RemoteSettingsDataSource {
    /**
     * Retrieves settings from the backend.
     *
     * @param userId The unique identifier of the user
     * @return Success with [Settings] or failure with [RemoteUserDataSourceError]
     */
    suspend fun getSettings(userId: UserId): ResultWithError<Settings, RemoteUserDataSourceError>

    /**
     * Changes user's UI language preference on the backend.
     *
     * Attempts to synchronize the change across all user's devices.
     *
     * @param userId The unique identifier of the user
     * @param language The new language preference
     * @return Success or failure with [ChangeUiLanguageRemoteDataSourceError]
     */
    suspend fun changeUiLanguage(
        userId: UserId,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError>
}
