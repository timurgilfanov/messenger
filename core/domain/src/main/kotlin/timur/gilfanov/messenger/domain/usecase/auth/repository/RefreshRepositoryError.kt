package timur.gilfanov.messenger.domain.usecase.auth.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for token refresh repository operations.
 *
 * ## Logical Errors
 * - [TokenExpired] - The refresh token has expired and the session cannot be renewed
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface RefreshRepositoryError {
    data object TokenExpired : RefreshRepositoryError
    data class LocalOperationFailed(val error: LocalStorageError) : RefreshRepositoryError
    data class RemoteOperationFailed(val error: RemoteError) : RefreshRepositoryError
}
