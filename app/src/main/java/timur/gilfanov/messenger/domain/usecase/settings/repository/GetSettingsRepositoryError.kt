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
     * Settings were not found and were reset to default values.
     *
     * Occurs when settings cannot be loaded from any available source
     * and the system automatically created default settings.
     */
    data object SettingsResetToDefaults : GetSettingsRepositoryError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : GetSettingsRepositoryError
}
