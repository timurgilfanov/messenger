package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors that can occur during logout.
 *
 * - [LocalOperationFailed] - local storage operation failed while clearing session
 * - [RemoteOperationFailed] - remote logout call failed
 */
sealed interface LogoutError {
    data class LocalOperationFailed(val error: LocalStorageError) : LogoutError
    data class RemoteOperationFailed(val error: RemoteError) : LogoutError
}

internal fun LogoutRepositoryError.toUseCaseError(): LogoutError = when (this) {
    is LogoutRepositoryError.LocalOperationFailed -> LogoutError.LocalOperationFailed(error)
    is LogoutRepositoryError.RemoteOperationFailed -> LogoutError.RemoteOperationFailed(error)
}
