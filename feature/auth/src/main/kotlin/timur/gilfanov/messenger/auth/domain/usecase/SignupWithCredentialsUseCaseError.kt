package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.usecase.auth.repository.PasswordValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupEmailError
import timur.gilfanov.messenger.domain.usecase.auth.repository.SignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

/**
 * Errors for [SignupWithCredentialsUseCase].
 *
 * ## Validation Errors
 * - [InvalidEmail] - Email failed local or server-side validation
 * - [InvalidPassword] - Password failed local or server-side validation
 *
 * ## Logical Errors
 * - [InvalidName] - The provided profile name failed client-side or server-side validation
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface SignupWithCredentialsUseCaseError {
    data class InvalidEmail(val reason: SignupEmailError) : SignupWithCredentialsUseCaseError
    data class InvalidPassword(val reason: PasswordValidationError) :
        SignupWithCredentialsUseCaseError
    data class InvalidName(val reason: ProfileNameValidationError) :
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
