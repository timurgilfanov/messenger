package timur.gilfanov.messenger.auth.domain.usecase

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.mapError
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.util.Logger

class LogoutUseCaseImpl(private val authRepository: AuthRepository, private val logger: Logger) :
    LogoutUseCase {

    companion object {
        private const val TAG = "LogoutUseCaseImpl"
    }

    override suspend fun invoke(): ResultWithError<Unit, LogoutError> =
        authRepository.logout().mapError { error ->
            logger.e(TAG, "Repository logout failed: $error")
            error.toUseCaseError()
        }
}
