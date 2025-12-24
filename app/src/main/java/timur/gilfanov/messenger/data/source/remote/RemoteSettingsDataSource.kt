package timur.gilfanov.messenger.data.source.remote

import kotlin.time.Instant
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

/**
 * Remote data source for user settings data.
 *
 * Provides access to typed settings data and operations on the backend service.
 * Handles network communication for settings synchronization across devices.
 * Encapsulates all parsing logic between network DTOs and typed RemoteSettings.
 */
interface RemoteSettingsDataSource {
    /**
     * Retrieves settings from the remote server as a typed RemoteSettings object.
     *
     * @return Success with [RemoteSettings] or failure with [RemoteSettingsDataSourceError]
     */
    suspend fun get(): ResultWithError<RemoteSettings, RemoteSettingsDataSourceError>

    /**
     * Changes user's UI language preference on the remote server.
     *
     * @param language The new language preference
     * @return Success or failure with [ChangeUiLanguageRemoteDataSourceError]
     */
    suspend fun changeUiLanguage(
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeUiLanguageRemoteDataSourceError>

    /**
     * Pushes settings to the remote server.
     *
     * @param settings Settings payload to send to remote
     * @return Success or failure with [UpdateSettingsRemoteDataSourceError]
     */
    suspend fun put(settings: Settings): ResultWithError<Unit, UpdateSettingsRemoteDataSourceError>

    /**
     * Synchronizes a single setting with the remote server using Last Write Wins.
     *
     * Uses typed domain request for type safety. Remote data source validates and
     * converts domain types to wire format.
     *
     * @param request The typed sync request containing domain value and version information
     * @return Success with sync result (Success/Conflict) or failure with error
     */
    suspend fun syncSingleSetting(
        request: TypedSettingSyncRequest,
    ): ResultWithError<SyncResult, SyncSingleSettingError>

    /**
     * Synchronizes multiple settings in a batch request.
     *
     * Uses typed domain requests for type safety. Remote data source validates and
     * converts domain types to wire format.
     *
     * @param requests List of typed sync requests for different settings
     * @return Success with map of setting keys to sync results (Success/Conflict), or failure with batch-level error
     */
    suspend fun syncBatch(
        requests: List<TypedSettingSyncRequest>,
    ): ResultWithError<Map<String, SyncResult>, SyncBatchError>
}

/**
 * Error taxonomy for the "sync single setting" remote data source method.
 *
 * Sync operations can fail due to authentication or infrastructure errors.
 * When more specific errors or modifications are needed, use composition, not inheritance.
 */
typealias SyncSingleSettingError = RemoteSettingsDataSourceError

/**
 * Error taxonomy for the "sync batch" remote data source method.
 *
 * Batch-level errors affect all settings in the request (e.g., network down, authentication failed).
 * Per-setting results (Success/Conflict) are returned in the success map.
 * When more specific errors or modifications are needed, use composition, not inheritance.
 */
typealias SyncBatchError = RemoteSettingsDataSourceError

/**
 * A single setting item from remote server response.
 *
 * @property key Setting identifier (e.g., "ui_language")
 * @property value Raw string value from server
 * @property version Server's version number for this setting
 */
data class RemoteSettingDto(val key: String, val value: String, val version: Int)

/**
 * Generic setting synchronization request with typed domain value.
 *
 * Contains the domain value and metadata needed for Last Write Wins conflict resolution.
 * Wrapped by [TypedSettingSyncRequest] sealed interface for type safety.
 *
 * @param T The type of the setting value (e.g., UiLanguage)
 * @property value The typed domain value to sync
 * @property clientVersion Current local version number
 * @property lastKnownServerVersion Last server version known to client (0 if never synced)
 * @property modifiedAt Timestamp of local modification (for conflict resolution)
 */
data class SettingSyncRequest<T>(
    val value: T,
    val clientVersion: Int,
    val lastKnownServerVersion: Int,
    val modifiedAt: Instant,
)

/**
 * Result of a setting synchronization operation using Last Write Wins strategy.
 *
 * The server compares modification timestamps and version numbers to detect conflicts.
 * When both client and server modified the same setting, the newer timestamp wins.
 */
sealed class SyncResult {
    /**
     * Sync succeeded without conflict - client's value accepted by server.
     *
     * @property newVersion The new version number assigned by server
     */
    data class Success(val newVersion: Int) : SyncResult()

    /**
     * Conflict detected - server has a newer modification that overwrites client's change.
     *
     * Client should accept the server value and notify user if appropriate.
     *
     * @property serverValue The winning value from server
     * @property serverVersion Server's version number before this sync
     * @property newVersion New version number after conflict resolution
     * @property serverModifiedAt Timestamp of server's modification
     */
    data class Conflict(
        val serverValue: String,
        val serverVersion: Int,
        val newVersion: Int,
        val serverModifiedAt: Instant,
    ) : SyncResult()
}
