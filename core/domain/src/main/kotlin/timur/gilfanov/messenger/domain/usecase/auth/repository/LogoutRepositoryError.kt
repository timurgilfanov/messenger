package timur.gilfanov.messenger.domain.usecase.auth.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for logout repository operations.
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface LogoutRepositoryError {
    data class LocalOperationFailed(val error: LocalStorageError) : LogoutRepositoryError
    data class RemoteOperationFailed(val error: RemoteError) : LogoutRepositoryError
}
