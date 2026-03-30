package timur.gilfanov.messenger.domain.usecase.auth.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

/**
 * Errors for account signup repository operations.
 *
 * ## Logical Errors
 * - [InvalidEmail] - The email was rejected by the server; see [SignupEmailError] for detail
 * - [InvalidPassword] - The password was rejected by the server; see [PasswordValidationError] for detail
 * - [InvalidName] - The profile name was rejected by the server; see [ProfileNameValidationError] for detail
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface SignupRepositoryError {
    data class InvalidEmail(val reason: SignupEmailError) : SignupRepositoryError
    data class InvalidPassword(val reason: PasswordValidationError) : SignupRepositoryError
    data class InvalidName(val reason: ProfileNameValidationError) : SignupRepositoryError
    data class LocalOperationFailed(val error: LocalStorageError) : SignupRepositoryError
    data class RemoteOperationFailed(val error: UnauthRemoteError) : SignupRepositoryError
}
