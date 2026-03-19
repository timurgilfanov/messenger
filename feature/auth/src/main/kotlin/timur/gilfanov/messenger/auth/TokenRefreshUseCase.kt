package timur.gilfanov.messenger.auth

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

/**
 * Refreshes the current access token using the stored refresh token.
 *
 * On terminal errors ([TokenRefreshError.SessionExpired]), a logout is performed as a side effect
 * to transition the auth state to [timur.gilfanov.messenger.domain.entity.auth.AuthState.Unauthenticated].
 * The logout result is ignored; callers should treat [TokenRefreshError.SessionExpired] as the
 * authoritative signal that the session has ended.
 */
fun interface TokenRefreshUseCase {
    suspend operator fun invoke(): ResultWithError<AuthTokens, TokenRefreshError>
}
