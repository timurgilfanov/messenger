package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.settings.repository.GetSettingsRepositoryError

/**
 * Errors that can occur during settings observation operations.
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
sealed interface ObserveSettingsError {
    /**
     * Not authenticated.
     *
     * This error occurs when [timur.gilfanov.messenger.domain.usecase.auth.AuthRepository] cannot
     * provide an authenticated session needed to observe the settings.
     */
    data object Unauthorized : ObserveSettingsError

    /**
     * Settings were not found and were reset to default values.
     *
     * Occurs when settings cannot be loaded from any available source
     * and the system automatically created default settings.
     */
    data object SettingsResetToDefaults : ObserveSettingsError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : ObserveSettingsError
}

/**
 * Maps a [GetSettingsRepositoryError] to the corresponding [ObserveSettingsError].
 */
internal fun GetSettingsRepositoryError.toObserveSettingsError(): ObserveSettingsError =
    when (this) {
        GetSettingsRepositoryError.SettingsResetToDefaults ->
            ObserveSettingsError.SettingsResetToDefaults
        is GetSettingsRepositoryError.LocalOperationFailed ->
            ObserveSettingsError.LocalOperationFailed(error)
    }
