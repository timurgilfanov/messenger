package timur.gilfanov.messenger.domain.usecase.settings.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

/**
 * Errors for change language repository operations.
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 */
sealed interface ChangeLanguageRepositoryError {
    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : ChangeLanguageRepositoryError
}
