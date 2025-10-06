package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Settings
import timur.gilfanov.messenger.domain.entity.user.UiLanguage
import timur.gilfanov.messenger.domain.entity.user.UserId

/**
 * Repository for managing user settings data.
 *
 * Provides access to user settings and operations for updating preferences
 * such as UI language. Coordinates between remote and local data sources
 * to ensure settings are synchronized across devices.
 */
interface SettingsRepository {
    /**
     * Observes settings changes for a specific user.
     *
     * @param userId The unique identifier of the user to observe
     * @return Flow emitting settings updates or errors
     */
    fun observeSettings(userId: UserId): Flow<ResultWithError<Settings, UserRepositoryError>>

    /**
     * Changes user's UI language preference.
     *
     * @param userId The unique identifier of the user
     * @param language The new language preference
     * @return Success or failure with [ChangeLanguageRepositoryError]
     */
    suspend fun changeLanguage(
        userId: UserId,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError>
}
