package timur.gilfanov.messenger.auth.login

import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

/**
 * Errors for [LoginWithCredentialsUseCase].
 *
 * ## Validation Errors
 * - [ValidationFailed] - Client-side credential validation failed; contains the specific error
 *
 * ## Logical Errors
 * - [InvalidCredentials] - Email or password does not match any account
 * - [EmailNotVerified] - The account exists but the email address has not been verified
 * - [AccountSuspended] - The account has been suspended
 * - [InvalidEmail] - The email was rejected by the server
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface LoginUseCaseError {
    data class ValidationFailed(val error: CredentialsValidationError) : LoginUseCaseError
    data object InvalidCredentials : LoginUseCaseError
    data object EmailNotVerified : LoginUseCaseError
    data object AccountSuspended : LoginUseCaseError
    data class InvalidEmail(val reason: EmailValidationError) : LoginUseCaseError
    data class LocalOperationFailed(val error: LocalStorageError) : LoginUseCaseError
    data class RemoteOperationFailed(val error: UnauthRemoteError) : LoginUseCaseError
}

internal fun LoginRepositoryError.toUseCaseError(): LoginUseCaseError = when (this) {
    is LoginRepositoryError.InvalidEmail -> LoginUseCaseError.InvalidEmail(reason)
    LoginRepositoryError.InvalidCredentials -> LoginUseCaseError.InvalidCredentials
    LoginRepositoryError.EmailNotVerified -> LoginUseCaseError.EmailNotVerified
    LoginRepositoryError.AccountSuspended -> LoginUseCaseError.AccountSuspended
    is LoginRepositoryError.LocalOperationFailed -> LoginUseCaseError.LocalOperationFailed(error)
    is LoginRepositoryError.RemoteOperationFailed -> LoginUseCaseError.RemoteOperationFailed(error)
}
