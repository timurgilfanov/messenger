package timur.gilfanov.messenger.domain.usecase.settings.repository

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthSession
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
     * @param session The authenticated session identifying the user
     * @return Flow emitting settings updates or errors
     */
    fun observeSettings(
        session: AuthSession,
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
     * @param session The authenticated session identifying the user
     * @param language The new language preference
     * @return Success or failure with [ChangeLanguageRepositoryError]
     */
    suspend fun changeUiLanguage(
        session: AuthSession,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError>

    /**
     * Syncs a specific user setting with the remote backend.
     *
     * @param session Authenticated session identifying the user
     * @param key The concrete setting to sync
     */
    suspend fun syncSetting(
        session: AuthSession,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingRepositoryError>

    /**
     * Syncs all pending settings changes for specific users.
     *
     * @param session Authenticated session identifying the user
     */
    suspend fun syncAllPendingSettings(
        session: AuthSession,
    ): ResultWithError<Unit, SyncAllSettingsRepositoryError>
}
