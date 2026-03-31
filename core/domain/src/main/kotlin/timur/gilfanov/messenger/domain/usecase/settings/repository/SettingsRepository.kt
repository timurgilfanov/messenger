package timur.gilfanov.messenger.domain.usecase.settings.repository

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.UserScopeKey
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.entity.settings.Settings
import timur.gilfanov.messenger.domain.entity.settings.SettingsConflictEvent
import timur.gilfanov.messenger.domain.entity.settings.UiLanguage

/**
 * Repository for managing user settings.
 *
 * Provides access to user settings and operations for updating preferences
 * such as UI language.
 */
interface SettingsRepository {
    /**
     * Observes settings changes for the authenticated user.
     *
     * @param userKey The key identifying the user scope
     * @return Flow emitting settings updates or errors
     */
    fun observeSettings(
        userKey: UserScopeKey,
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
     * @param userKey The key identifying the user scope
     * @param language The new language preference
     * @return Success or failure with [ChangeLanguageRepositoryError]
     */
    suspend fun changeUiLanguage(
        userKey: UserScopeKey,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError>

    /**
     * Syncs a specific user setting with the remote backend.
     *
     * @param userKey The key identifying the user scope
     * @param key The concrete setting to sync
     */
    suspend fun syncSetting(
        userKey: UserScopeKey,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError>

    /**
     * Syncs all pending settings changes for specific users.
     *
     * @param userKey The key identifying the user scope
     */
    suspend fun syncAllPendingSettings(
        userKey: UserScopeKey,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError>
}
