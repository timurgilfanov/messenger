package timur.gilfanov.messenger.domain.usecase.auth.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

/**
 * Errors for email/password login repository operations.
 *
 * ## Logical Errors
 * - [InvalidEmail] - The email was rejected by the server; see [EmailValidationError] for detail
 * - [InvalidCredentials] - The password does not match the account
 * - [EmailNotVerified] - The account exists but the email address has not been verified
 * - [AccountSuspended] - The account has been suspended
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface LoginRepositoryError {
    data class InvalidEmail(val reason: EmailValidationError) : LoginRepositoryError
    data object InvalidCredentials : LoginRepositoryError
    data object EmailNotVerified : LoginRepositoryError
    data object AccountSuspended : LoginRepositoryError
    data class LocalOperationFailed(val error: LocalStorageError) : LoginRepositoryError
    data class RemoteOperationFailed(val error: UnauthRemoteError) : LoginRepositoryError
}
