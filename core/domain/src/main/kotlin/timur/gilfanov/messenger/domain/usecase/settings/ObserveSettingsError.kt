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
     * Settings could not be loaded and consumers received transient defaults.
     *
     * Occurs when settings cannot be loaded from any available source (e.g. local empty and
     * remote recovery failed offline). Default values are emitted transiently so the UI has
     * something to display; no row is persisted. Recovery is re-attempted on the next
     * subscription to `observeSettings`.
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
