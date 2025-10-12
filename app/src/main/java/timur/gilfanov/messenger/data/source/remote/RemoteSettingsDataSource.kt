package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage

/**
 * Remote data source for user settings data.
 *
 * Provides access to settings data and operations on the backend service.
 * Handles network communication for settings synchronization across devices.
 */
interface RemoteSettingsDataSource {
    /**
     * Retrieves settings from the remote server.
     *
     * @param identity The user identity for which to retrieve settings
     * @return Success with [Settings] or failure with [RemoteUserDataSourceError]
     */
    suspend fun getSettings(
        identity: Identity,
    ): ResultWithError<Settings, RemoteUserDataSourceError>

    /**
     * Changes user's UI language preference on the remote server.
     *
     * @param identity The user identity for which to change the language
     * @param language The new language preference
     * @return Success or failure with [ChangeUiLanguageRemoteDataSourceError]
     */
    suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError>

    suspend fun updateSettings(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError>
}
