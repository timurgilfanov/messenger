package timur.gilfanov.messenger.auth.domain.usecase

import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.repository.LogoutRepositoryError
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.util.Logger

class TokenRefreshUseCaseImpl(
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : TokenRefreshUseCase {

    companion object {
        private const val TAG = "TokenRefreshUseCaseImpl"
    }

    override suspend fun invoke(): ResultWithError<AuthTokens, TokenRefreshError> {
        authRepository.authState.first()
        return authRepository.refreshToken().fold(
            onSuccess = { tokens ->
                ResultWithError.Success(tokens)
            },
            onFailure = { error ->
                when (error) {
                    is RefreshRepositoryError.TokenExpired,
                    is RefreshRepositoryError.SessionRevoked,
                    -> handleRefreshError()

                    is RefreshRepositoryError.LocalOperationFailed -> {
                        logger.e(TAG, "Local operation failed during token refresh: ${error.error}")
                        ResultWithError.Failure(TokenRefreshError.LocalOperationFailed(error.error))
                    }

                    is RefreshRepositoryError.RemoteOperationFailed -> {
                        logger.e(
                            TAG,
                            "Remote operation failed during token refresh: ${error.error}",
                        )
                        ResultWithError.Failure(
                            TokenRefreshError.RemoteOperationFailed(error.error),
                        )
                    }
                }
            },
        )
    }

    private suspend fun handleRefreshError(): ResultWithError<AuthTokens, TokenRefreshError> {
        val logoutResult = authRepository.logout()
        if (logoutResult is ResultWithError.Failure) {
            logger.e(
                TAG,
                "Logout failed during session expiry handling: ${logoutResult.error}",
            )
        }
        val localLogoutError = (logoutResult as? ResultWithError.Failure)
            ?.error as? LogoutRepositoryError.LocalOperationFailed
        return if (localLogoutError != null) {
            ResultWithError.Failure(TokenRefreshError.LocalOperationFailed(localLogoutError.error))
        } else {
            ResultWithError.Failure(TokenRefreshError.SessionExpired)
        }
    }
}
