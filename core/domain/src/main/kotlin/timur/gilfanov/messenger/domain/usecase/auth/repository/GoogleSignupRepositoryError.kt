package timur.gilfanov.messenger.domain.usecase.auth.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

/**
 * Errors for Google signup repository operations.
 *
 * ## Logical Errors
 * - [InvalidToken] - The provided Google ID token is malformed or expired
 * - [AccountAlreadyExists] - An account is already associated with the Google identity
 * - [InvalidName] - The provided profile name failed server-side validation
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface GoogleSignupRepositoryError {
    data object InvalidToken : GoogleSignupRepositoryError
    data object AccountAlreadyExists : GoogleSignupRepositoryError
    data class InvalidName(val reason: ProfileNameValidationError) : GoogleSignupRepositoryError
    data class LocalOperationFailed(val error: LocalStorageError) : GoogleSignupRepositoryError
    data class RemoteOperationFailed(val error: UnauthRemoteError) : GoogleSignupRepositoryError
}
