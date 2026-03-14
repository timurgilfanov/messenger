package timur.gilfanov.messenger.domain.usecase.auth.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for account signup repository operations.
 *
 * ## Logical Errors
 * - [EmailTaken] - An account with the given email already exists
 * - [InvalidName] - The provided profile name is not acceptable
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface SignupRepositoryError {
    data object EmailTaken : SignupRepositoryError
    data object InvalidName : SignupRepositoryError
    data class LocalOperationFailed(val error: LocalStorageError) : SignupRepositoryError
    data class RemoteOperationFailed(val error: RemoteError) : SignupRepositoryError
}
