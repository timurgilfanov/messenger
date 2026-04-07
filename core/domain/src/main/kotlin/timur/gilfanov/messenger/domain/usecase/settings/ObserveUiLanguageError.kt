package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError

/**
 * Errors that can occur during UI language observation operations.
 *
 * ## Auth Errors
 * - [Unauthorized] - Not authenticated
 *
 * ## Logical Errors
 * - [SettingsResetToDefaults] - Settings were not found and reset to defaults
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 */
sealed interface ObserveUiLanguageError {
    /**
     * Not authenticated.
     *
     * This error occurs when [timur.gilfanov.messenger.domain.usecase.auth.AuthRepository] cannot
     * provide an authenticated session needed to observe the language preference.
     */
    data object Unauthorized : ObserveUiLanguageError

    /**
     * Settings were not found and were reset to default values.
     *
     * Occurs when settings cannot be loaded from any available source
     * and the system automatically created default settings.
     */
    data object SettingsResetToDefaults : ObserveUiLanguageError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : ObserveUiLanguageError
}

/**
 * Maps a [GetSettingsRepositoryError] to the corresponding [ObserveUiLanguageError].
 */
internal fun GetSettingsRepositoryError.toObserveUiLanguageError(): ObserveUiLanguageError =
    when (this) {
        GetSettingsRepositoryError.SettingsResetToDefaults ->
            ObserveUiLanguageError.SettingsResetToDefaults
        is GetSettingsRepositoryError.LocalOperationFailed ->
            ObserveUiLanguageError.LocalOperationFailed(error)
    }
