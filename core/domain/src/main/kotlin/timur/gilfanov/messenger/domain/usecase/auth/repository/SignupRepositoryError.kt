package timur.gilfanov.messenger.domain.usecase.auth.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for account signup repository operations.
 *
 * ## Logical Errors
 * - [InvalidEmail] - The email was rejected by the server; see [EmailValidationError] for detail
 * - [InvalidName] - The profile name was rejected by the server; see [ProfileNameValidationError] for detail
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface SignupRepositoryError {
    data object InvalidEmail : SignupRepositoryError, EmailValidationError
    data object InvalidName : SignupRepositoryError, ProfileNameValidationError
    data class LocalOperationFailed(val error: LocalStorageError) : SignupRepositoryError
    data class RemoteOperationFailed(val error: RemoteError) : SignupRepositoryError
}
