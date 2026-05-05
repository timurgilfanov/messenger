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
sealed interface LogoutUseCaseError {
    data class LocalOperationFailed(val error: LocalStorageError) : LogoutUseCaseError
    data class RemoteOperationFailed(val error: RemoteError) : LogoutUseCaseError
}

internal fun LogoutRepositoryError.toUseCaseError(): LogoutUseCaseError = when (this) {
    is LogoutRepositoryError.LocalOperationFailed -> LogoutUseCaseError.LocalOperationFailed(error)
    is LogoutRepositoryError.RemoteOperationFailed -> LogoutUseCaseError.RemoteOperationFailed(
        error,
    )
}
