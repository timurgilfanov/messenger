package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleSignupRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.ProfileNameValidationError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

/**
 * Errors for [SignupWithGoogleUseCase].
 *
 * ## Logical Errors
 * - [InvalidToken] - The provided Google ID token is malformed or expired
 * - [AccountAlreadyExists] - An account is already associated with the Google identity
 * - [InvalidName] - The provided profile name failed validation
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface SignupWithGoogleUseCaseError {
    data object InvalidToken : SignupWithGoogleUseCaseError
    data object AccountAlreadyExists : SignupWithGoogleUseCaseError
    data class InvalidName(val reason: ProfileNameValidationError) : SignupWithGoogleUseCaseError
    data class LocalOperationFailed(val error: LocalStorageError) : SignupWithGoogleUseCaseError
    data class RemoteOperationFailed(val error: UnauthRemoteError) : SignupWithGoogleUseCaseError
}

internal fun GoogleSignupRepositoryError.toUseCaseError(): SignupWithGoogleUseCaseError =
    when (this) {
        GoogleSignupRepositoryError.InvalidToken -> SignupWithGoogleUseCaseError.InvalidToken

        GoogleSignupRepositoryError.AccountAlreadyExists ->
            SignupWithGoogleUseCaseError.AccountAlreadyExists

        is GoogleSignupRepositoryError.InvalidName ->
            SignupWithGoogleUseCaseError.InvalidName(reason)

        is GoogleSignupRepositoryError.LocalOperationFailed ->
            SignupWithGoogleUseCaseError.LocalOperationFailed(error)

        is GoogleSignupRepositoryError.RemoteOperationFailed ->
            SignupWithGoogleUseCaseError.RemoteOperationFailed(error)
    }
