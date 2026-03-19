package timur.gilfanov.messenger.auth

import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

/**
 * Errors that can occur when refreshing the authentication token.
 *
 * ## Terminal Errors (trigger logout)
 * - [SessionExpired] — the session can no longer be renewed; covers both token expiry
 *   and explicit revocation, which are indistinguishable at the use-case level
 *
 * ## Transient Errors (do not affect session state)
 * - [LocalOperationFailed] — local storage read/write failed
 * - [RemoteOperationFailed] — remote token exchange failed
 */
sealed interface TokenRefreshError {
    data object SessionExpired : TokenRefreshError
    data class LocalOperationFailed(val error: LocalStorageError) : TokenRefreshError
    data class RemoteOperationFailed(val error: RemoteError) : TokenRefreshError
}

internal fun RefreshRepositoryError.toUseCaseError(): TokenRefreshError = when (this) {
    is RefreshRepositoryError.TokenExpired -> TokenRefreshError.SessionExpired
    is RefreshRepositoryError.SessionRevoked -> TokenRefreshError.SessionExpired
    is RefreshRepositoryError.LocalOperationFailed ->
        TokenRefreshError.LocalOperationFailed(error)
    is RefreshRepositoryError.RemoteOperationFailed ->
        TokenRefreshError.RemoteOperationFailed(error)
}
