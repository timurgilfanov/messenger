package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Local data source for user settings data.
 *
 * Provides access to locally cached settings. Acts as the single source
 * of truth for settings data in the app, with updates synchronized from
 * remote data source.
 */
interface LocalSettingsDataSource {
    /**
     * Observes settings changes for a specific user.
     *
     * @param userId The unique identifier of the user to observe
     * @return Flow emitting settings updates or errors
     */
    fun observeSettings(
        userId: UserId,
    ): Flow<ResultWithError<Settings, GetSettingsLocalDataSourceError>>

    /**
     * Updates settings using a transformation function.
     *
     * Atomically reads the current settings, applies the transformation,
     * and writes the result back to local storage.
     *
     * @param userId The unique identifier of the user
     * @param transform Function transforming the current settings to new settings
     * @return Success or failure with [LocalUserDataSourceError]
     */
    suspend fun updateSettings(
        userId: UserId,
        transform: (Settings) -> Settings,
    ): ResultWithError<Unit, UpdateSettingsLocalDataSourceError>

    /**
     * Inserts settings for a user.
     *
     * Creates new settings entry in local storage. Typically used when restoring
     * settings from remote or initializing settings for the first time.
     *
     * @param userId The unique identifier of the user
     * @param settings The settings to insert
     * @return Success or failure with [InsertSettingsLocalDataSourceError]
     */
    suspend fun insertSettings(
        userId: UserId,
        settings: Settings,
    ): ResultWithError<Unit, InsertSettingsLocalDataSourceError>

    /**
     * Resets settings to default values.
     *
     * Replaces current settings with default values. Used as a fallback when
     * settings cannot be restored from remote or are corrupted.
     *
     * @param userId The unique identifier of the user
     * @return Success or failure with [ResetSettingsLocalDataSourceError]
     */
    suspend fun resetSettings(
        userId: UserId,
    ): ResultWithError<Unit, ResetSettingsLocalDataSourceError>
}
