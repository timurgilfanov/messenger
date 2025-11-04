package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
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
     * Retrieves settings from the remote server.
     *
     * @param identity The user identity for which to retrieve settings
     * @return Success with [Settings] or failure with [RemoteUserDataSourceError]
     */
    suspend fun get(identity: Identity): ResultWithError<Settings, RemoteUserDataSourceError>

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

    /**
     * Pushes settings to the remote server.
     *
     * @param identity Identity whose settings should be updated
     * @param settings Settings payload to send to remote
     * @return Success or failure with [UpdateSettingsRemoteDataSourceError]
     */
    suspend fun put(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError>

    /**
     * Synchronizes a single setting with the remote server using Last Write Wins.
     *
     * @param request The sync request containing setting data and version information
     * @return Sync result indicating success, conflict, or error
     */
    suspend fun syncSingleSetting(request: SettingSyncRequest): SyncResult

    /**
     * Synchronizes multiple settings in a batch request.
     *
     * @param requests List of sync requests for different settings
     * @return Map of setting keys to their sync results
     */
    suspend fun syncBatch(requests: List<SettingSyncRequest>): Map<String, SyncResult>
}

data class SettingSyncRequest(
    val userId: UserId,
    val key: String,
    val value: String,
    val clientVersion: Int,
    val lastKnownServerVersion: Int,
    val modifiedAt: Long,
)

sealed class SyncResult {
    data class Success(val newVersion: Int) : SyncResult()

    data class Conflict(
        val serverValue: String,
        val serverVersion: Int,
        val newVersion: Int,
        val serverModifiedAt: Long,
    ) : SyncResult()

    data class Error(val message: String) : SyncResult()
}
