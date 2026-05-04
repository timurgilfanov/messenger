package timur.gilfanov.messenger.domain.usecase.settings.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

/**
 * Errors for get settings repository operations.
 *
 * ## Logical Errors
 * - [SettingsResetToDefaults] - Settings were not found and reset to defaults
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 */
sealed interface GetSettingsRepositoryError {
    /**
     * Settings could not be loaded and consumers received transient defaults.
     *
     * Occurs when settings cannot be loaded from any available source (e.g. local empty and
     * remote recovery failed offline). Default values are emitted transiently so the UI has
     * something to display; no row is persisted. Recovery is re-attempted on the next
     * subscription to `observeSettings`.
     */
    data object SettingsResetToDefaults : GetSettingsRepositoryError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : GetSettingsRepositoryError
}
