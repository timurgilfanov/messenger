package timur.gilfanov.messenger.auth.domain.usecase

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
sealed interface TokenRefreshUseCaseError {
    data object SessionExpired : TokenRefreshUseCaseError
    data class LocalOperationFailed(val error: LocalStorageError) : TokenRefreshUseCaseError
    data class RemoteOperationFailed(val error: RemoteError) : TokenRefreshUseCaseError
}

internal fun RefreshRepositoryError.toUseCaseError(): TokenRefreshUseCaseError = when (this) {
    is RefreshRepositoryError.TokenExpired -> TokenRefreshUseCaseError.SessionExpired

    is RefreshRepositoryError.SessionRevoked -> TokenRefreshUseCaseError.SessionExpired

    is RefreshRepositoryError.LocalOperationFailed ->
        TokenRefreshUseCaseError.LocalOperationFailed(error)

    is RefreshRepositoryError.RemoteOperationFailed ->
        TokenRefreshUseCaseError.RemoteOperationFailed(error)
}
