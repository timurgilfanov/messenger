package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthTokens
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.auth.repository.RefreshRepositoryError
import timur.gilfanov.messenger.util.Logger

class TokenRefreshUseCaseImpl(
    private val authRepository: AuthRepository,
    private val logger: Logger,
) : TokenRefreshUseCase {

    companion object {
        private const val TAG = "TokenRefreshUseCaseImpl"
    }

    override suspend fun invoke(): ResultWithError<AuthTokens, TokenRefreshError> =
        authRepository.refreshToken().fold(
            onSuccess = { tokens -> ResultWithError.Success(tokens) },
            onFailure = { error ->
                when (error) {
                    is RefreshRepositoryError.TokenExpired,
                    is RefreshRepositoryError.SessionRevoked,
                    -> {
                        authRepository.logout()
                        ResultWithError.Failure(TokenRefreshError.SessionExpired)
                    }

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
