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
 * - [SettingsUnspecified] - Settings are unspecified
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
     * Settings are unspecified.
     */
    data object SettingsUnspecified : ObserveSettingsError

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
        GetSettingsRepositoryError.SettingsUnspecified ->
            ObserveSettingsError.SettingsUnspecified
        is GetSettingsRepositoryError.LocalOperationFailed ->
            ObserveSettingsError.LocalOperationFailed(error)
    }
