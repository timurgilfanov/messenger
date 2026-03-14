package timur.gilfanov.messenger.domain.usecase.auth.repository

import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors for Google login repository operations.
 *
 * ## Logical Errors
 * - [InvalidToken] - The provided Google ID token is malformed or expired
 * - [AccountNotFound] - No account is associated with the Google identity
 *
 * ## Data Source Errors
 * - [LocalOperationFailed] - Local storage operation failed
 * - [RemoteOperationFailed] - Remote operation failed
 */
sealed interface GoogleLoginRepositoryError {
    data object InvalidToken : GoogleLoginRepositoryError
    data object AccountNotFound : GoogleLoginRepositoryError
    data class LocalOperationFailed(val error: LocalStorageError) : GoogleLoginRepositoryError
    data class RemoteOperationFailed(val error: RemoteError) : GoogleLoginRepositoryError
}
