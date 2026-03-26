package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.auth.validation.CredentialsValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.EmailValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

/**
 * Errors for [SignupWithCredentialsUseCase].
 *
 * ## Validation Errors
 * - [ValidationFailed] - Client-side credential validation failed; contains the specific error
 *
 * ## Logical Errors
 * - [InvalidName] - The provided profile name failed client-side or server-side validation
 * - [InvalidEmail] - The email was rejected by the server
 * - [InvalidPassword] - The password was rejected by the server
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface SignupWithCredentialsUseCaseError {
    data class ValidationFailed(val error: CredentialsValidationError) :
        SignupWithCredentialsUseCaseError

    data class InvalidName(val reason: ProfileNameValidationError) :
        SignupWithCredentialsUseCaseError

    data class InvalidEmail(val reason: EmailValidationError) : SignupWithCredentialsUseCaseError
    data class InvalidPassword(val reason: PasswordValidationError) :
        SignupWithCredentialsUseCaseError

    data class LocalOperationFailed(val error: LocalStorageError) :
        SignupWithCredentialsUseCaseError

    data class RemoteOperationFailed(val error: UnauthRemoteError) :
        SignupWithCredentialsUseCaseError
}

internal fun SignupRepositoryError.toUseCaseError(): SignupWithCredentialsUseCaseError =
    when (this) {
        is SignupRepositoryError.InvalidEmail ->
            SignupWithCredentialsUseCaseError.InvalidEmail(reason)

        is SignupRepositoryError.InvalidPassword ->
            SignupWithCredentialsUseCaseError.InvalidPassword(reason)

        is SignupRepositoryError.InvalidName ->
            SignupWithCredentialsUseCaseError.InvalidName(reason)

        is SignupRepositoryError.LocalOperationFailed ->
            SignupWithCredentialsUseCaseError.LocalOperationFailed(error)

        is SignupRepositoryError.RemoteOperationFailed ->
            SignupWithCredentialsUseCaseError.RemoteOperationFailed(error)
    }
