package timur.gilfanov.messenger.auth.login

import timur.gilfanov.messenger.domain.usecase.auth.repository.GoogleLoginRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.UnauthRemoteError

/**
 * Errors for [LoginWithGoogleUseCase].
 *
 * ## Logical Errors
 * - [InvalidToken] - The provided Google ID token is malformed or expired
 * - [AccountNotFound] - No account is associated with the Google identity
 * - [AccountSuspended] - The account has been suspended
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface GoogleLoginUseCaseError {
    data object InvalidToken : GoogleLoginUseCaseError
    data object AccountNotFound : GoogleLoginUseCaseError
    data object AccountSuspended : GoogleLoginUseCaseError
    data class LocalOperationFailed(val error: LocalStorageError) : GoogleLoginUseCaseError
    data class RemoteOperationFailed(val error: UnauthRemoteError) : GoogleLoginUseCaseError
}

internal fun GoogleLoginRepositoryError.toUseCaseError(): GoogleLoginUseCaseError = when (this) {
    GoogleLoginRepositoryError.InvalidToken -> GoogleLoginUseCaseError.InvalidToken
    GoogleLoginRepositoryError.AccountNotFound -> GoogleLoginUseCaseError.AccountNotFound
    GoogleLoginRepositoryError.AccountSuspended -> GoogleLoginUseCaseError.AccountSuspended
    is GoogleLoginRepositoryError.LocalOperationFailed ->
        GoogleLoginUseCaseError.LocalOperationFailed(error)
    is GoogleLoginRepositoryError.RemoteOperationFailed ->
        GoogleLoginUseCaseError.RemoteOperationFailed(error)
}
