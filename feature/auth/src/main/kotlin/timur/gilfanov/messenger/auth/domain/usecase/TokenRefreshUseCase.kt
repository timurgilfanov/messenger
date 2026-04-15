package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

/**
 * Refreshes the current access token using the stored refresh token.
 *
 * On terminal refresh failures (token expired or revoked), a logout is performed to transition
 * the auth state to [timur.gilfanov.messenger.domain.entity.auth.AuthState.Unauthenticated] and
 * delete user-scoped settings data. If the local session clearing fails, [TokenRefreshError.LocalOperationFailed]
 * is returned instead; the session is not transitioned and settings cleanup is skipped in that case.
 * Otherwise, [TokenRefreshError.SessionExpired] is the authoritative signal that the session has ended.
 *
 * On a successful refresh with token rotation, the previous scope's settings data is deleted,
 * since the old scope key becomes unreachable after the refresh token changes.
 */
fun interface TokenRefreshUseCase {
    suspend operator fun invoke(): ResultWithError<AuthTokens, TokenRefreshError>
}
