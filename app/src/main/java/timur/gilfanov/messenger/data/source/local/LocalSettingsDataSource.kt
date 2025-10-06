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
    fun observeSettings(userId: UserId): Flow<ResultWithError<Settings, LocalUserDataSourceError>>

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
    ): ResultWithError<Unit, LocalUserDataSourceError>
}
