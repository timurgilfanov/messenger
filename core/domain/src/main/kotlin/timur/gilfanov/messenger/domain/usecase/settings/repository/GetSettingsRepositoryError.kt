package timur.gilfanov.messenger.domain.usecase.settings.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

/**
 * Errors for get settings repository operations.
 *
 * ## Logical Errors
 * - [SettingsUnspecified] - Settings are currently unavailable and no stored value is specified
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 */
sealed interface GetSettingsRepositoryError {
    /**
     * Settings are currently unavailable and no stored value is specified.
     */
    data object SettingsUnspecified : GetSettingsRepositoryError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : GetSettingsRepositoryError
}
