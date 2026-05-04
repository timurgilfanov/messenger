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
     *
     * Consumers choose the fallback appropriate to their operation. Observation emits
     * transient defaults without persistence so the UI has something to display, while
     * language change persists the user's chosen value as a fresh row.
     */
    data object SettingsUnspecified : GetSettingsRepositoryError

    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : GetSettingsRepositoryError
}
