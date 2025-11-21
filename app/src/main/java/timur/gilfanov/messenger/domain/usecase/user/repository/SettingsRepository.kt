package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.SettingKey
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
     * Syncs a specific user setting with the remote backend.
     *
     * @param userId Identifier of the user whose setting is being synced
     * @param key The concrete setting to sync
     */
    suspend fun syncSetting(
        identity: Identity,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError>

    /**
     * Syncs all pending settings changes for all users.
     */
    suspend fun syncAllPendingSettings(
        identity: Identity,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError>
}
