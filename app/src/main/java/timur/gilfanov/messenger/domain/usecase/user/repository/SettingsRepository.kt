package timur.gilfanov.messenger.domain.usecase.user.repository

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.Identity
import timur.gilfanov.messenger.domain.entity.user.Settings
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
    fun observeSettings(identity: Identity): Flow<ResultWithError<Settings, UserRepositoryError>>

    /**
     * Changes user's UI language preference.
     *
     * @param identity The user identity for which to change the language
     * @param language The new language preference
     * @return Success or failure with [ChangeLanguageRepositoryError]
     */
    suspend fun changeLanguage(
        identity: Identity,
        language: UiLanguage,
    ): ResultWithError<Unit, ChangeLanguageRepositoryError>
}
