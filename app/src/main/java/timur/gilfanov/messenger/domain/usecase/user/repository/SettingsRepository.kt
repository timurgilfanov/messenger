package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.user.UiLanguage

/**
 * Repository for managing user settings.
 *
 * Provides access to user settings and operations for updating preferences
 * such as UI language.
 */
interface SettingsRepository {
    /**
     * Observes settings changes for a specific user.
     *
     * @param identity The user identity for which to observe settings
     * @return Flow emitting settings updates or errors
     */
    fun observeSettings(
        identity: Identity,
    ): Flow<ResultWithError<Settings, GetSettingsRepositoryError>>

    /**
     * Observes settings conflicts that occur during synchronization.
     *
     * Conflicts are emitted when a local change is overridden by a more recent
     * change from another device during the sync process.
     *
     * @return Flow emitting conflict events
     */
    fun observeConflicts(): Flow<SettingsConflictEvent>

    /**
     * Changes user's UI language preference.
     *
     * @param identity The user identity for which to change the language
     * @param language The new language preference
     * @return Success or failure with [ChangeLanguageRepositoryError]
     */
    suspend fun changeUiLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError>

    /**
     * Applies a snapshot received from remote to the local cache.
     *
     * @param identity Identity whose settings should be replaced
     * @param settings Snapshot fetched from remote storage
     * @return Success or failure with [ApplyRemoteSettingsRepositoryError]
     */
    suspend fun applyRemoteSettings(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, ApplyRemoteSettingsRepositoryError>

    /**
     * Pushes locally modified settings to remote storage.
     *
     * @param identity Identity whose settings should be synced
     * @param settings Settings payload to send to remote
     * @return Success or failure with [SyncLocalToRemoteRepositoryError]
     */
    suspend fun syncLocalToRemote(
        identity: Identity,
        settings: Settings,
    ): ResultWithError<Unit, SyncLocalToRemoteRepositoryError>
}
