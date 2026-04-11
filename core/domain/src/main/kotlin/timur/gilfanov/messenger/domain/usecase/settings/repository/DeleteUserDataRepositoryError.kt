package timur.gilfanov.messenger.domain.usecase.settings.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

/**
 * Errors for delete user data repository operations.
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed while deleting settings rows
 */
sealed interface DeleteUserDataRepositoryError {
    /**
     * Local storage operation failed.
     *
     * @property error The underlying [LocalStorageError] instance
     */
    data class LocalOperationFailed(val error: LocalStorageError) : DeleteUserDataRepositoryError
}
