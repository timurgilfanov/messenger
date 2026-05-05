package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens

/**
 * Refreshes the current access token using the stored refresh token.
 *
 * On terminal errors ([TokenRefreshUseCaseError.SessionExpired]), a logout is performed as a side effect
 * to transition the auth state to [timur.gilfanov.messenger.domain.entity.auth.AuthState.Unauthenticated].
 * If logout itself fails with a local storage error, [TokenRefreshUseCaseError.LocalOperationFailed] is
 * returned instead of [TokenRefreshUseCaseError.SessionExpired]. Otherwise callers should treat
 * [TokenRefreshUseCaseError.SessionExpired] as the authoritative signal that the session has ended.
 */
fun interface TokenRefreshUseCase {
    suspend operator fun invoke(): ResultWithError<AuthTokens, TokenRefreshUseCaseError>
}
