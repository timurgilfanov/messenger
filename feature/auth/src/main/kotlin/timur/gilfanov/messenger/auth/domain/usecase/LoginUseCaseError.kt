package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginEmailError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

/**
 * Errors for [LoginWithCredentialsUseCase].
 *
 * ## Validation Errors
 * - [InvalidEmail] - Email failed local or server-side validation
 * - [InvalidPassword] - Password failed local validation
 *
 * ## Logical Errors
 * - [InvalidCredentials] - Email or password does not match any account
 * - [EmailNotVerified] - The account exists but the email address has not been verified
 * - [AccountSuspended] - The account has been suspended
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface LoginUseCaseError {
    data class InvalidEmail(val reason: LoginEmailError) : LoginUseCaseError
    data class InvalidPassword(val reason: PasswordValidationError) : LoginUseCaseError
    data object InvalidCredentials : LoginUseCaseError
    data object EmailNotVerified : LoginUseCaseError
    data object AccountSuspended : LoginUseCaseError
    data class LocalOperationFailed(val error: LocalStorageError) : LoginUseCaseError
    data class RemoteOperationFailed(val error: UnauthRemoteError) : LoginUseCaseError
}

internal fun LoginRepositoryError.toUseCaseError(): LoginUseCaseError = when (this) {
    is LoginRepositoryError.InvalidEmail ->
        LoginUseCaseError.InvalidEmail(reason)
    LoginRepositoryError.InvalidCredentials -> LoginUseCaseError.InvalidCredentials
    LoginRepositoryError.EmailNotVerified -> LoginUseCaseError.EmailNotVerified
    LoginRepositoryError.AccountSuspended -> LoginUseCaseError.AccountSuspended
    is LoginRepositoryError.LocalOperationFailed ->
        LoginUseCaseError.LocalOperationFailed(error)
    is LoginRepositoryError.RemoteOperationFailed ->
        LoginUseCaseError.RemoteOperationFailed(error)
}
