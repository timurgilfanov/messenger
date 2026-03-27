package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.auth.domain.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.LoginRepositoryError
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
    data class InvalidEmail(val reason: EmailValidationUseCaseError) : LoginUseCaseError
    data class InvalidPassword(val reason: PasswordValidationUseCaseError) : LoginUseCaseError
    data object InvalidCredentials : LoginUseCaseError
    data object EmailNotVerified : LoginUseCaseError
    data object AccountSuspended : LoginUseCaseError
    data class LocalOperationFailed(val error: LocalStorageError) : LoginUseCaseError
    data class RemoteOperationFailed(val error: UnauthRemoteError) : LoginUseCaseError
}

internal fun LoginRepositoryError.toUseCaseError(): LoginUseCaseError = when (this) {
    is LoginRepositoryError.InvalidEmail ->
        LoginUseCaseError.InvalidEmail(reason.toEmailUseCaseError())
    LoginRepositoryError.InvalidCredentials -> LoginUseCaseError.InvalidCredentials
    LoginRepositoryError.EmailNotVerified -> LoginUseCaseError.EmailNotVerified
    LoginRepositoryError.AccountSuspended -> LoginUseCaseError.AccountSuspended
    is LoginRepositoryError.LocalOperationFailed ->
        LoginUseCaseError.LocalOperationFailed(error)
    is LoginRepositoryError.RemoteOperationFailed ->
        LoginUseCaseError.RemoteOperationFailed(error)
}

internal fun CredentialsValidationError.Email.toUseCaseError(): EmailValidationUseCaseError =
    when (this) {
        CredentialsValidationError.Email.BlankEmail ->
            EmailValidationUseCaseError.BlankEmail
        CredentialsValidationError.Email.InvalidEmailFormat ->
            EmailValidationUseCaseError.InvalidEmailFormat
        CredentialsValidationError.Email.NoAtInEmail ->
            EmailValidationUseCaseError.NoAtInEmail
        is CredentialsValidationError.Email.EmailTooLong ->
            EmailValidationUseCaseError.EmailTooLong(maxLength)
        CredentialsValidationError.Email.NoDomainAtEmail ->
            EmailValidationUseCaseError.NoDomainAtEmail
    }

internal fun CredentialsValidationError.Password.toUseCaseError(): PasswordValidationUseCaseError =
    when (this) {
        is CredentialsValidationError.Password.PasswordTooShort ->
            PasswordValidationUseCaseError.PasswordTooShort(minLength)
        is CredentialsValidationError.Password.PasswordTooLong ->
            PasswordValidationUseCaseError.PasswordTooLong(maxLength)
        is CredentialsValidationError.Password.PasswordMustContainNumbers ->
            PasswordValidationUseCaseError.PasswordMustContainNumbers(minNumbers)
        is CredentialsValidationError.Password.PasswordMustContainAlphabet ->
            PasswordValidationUseCaseError.PasswordMustContainAlphabet(minAlphabet)
    }

internal fun EmailValidationError.toEmailUseCaseError(): EmailValidationUseCaseError = when (this) {
    EmailValidationError.EmailTaken -> EmailValidationUseCaseError.EmailTaken
    EmailValidationError.EmailNotExists -> EmailValidationUseCaseError.EmailNotExists
    is EmailValidationError.UnknownRuleViolation ->
        EmailValidationUseCaseError.UnknownRuleViolation(reason)
}
