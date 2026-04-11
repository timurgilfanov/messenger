package timur.gilfanov.messenger.auth.domain.usecase

import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.mapError
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

class LogoutUseCaseImpl(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) : LogoutUseCase {

    companion object {
        private const val TAG = "LogoutUseCaseImpl"
    }

    override suspend fun invoke(): ResultWithError<Unit, LogoutError> {
        val authState = authRepository.authState.first()
        if (authState is AuthState.Authenticated) {
            val userKey = authState.session.toUserScopeKey()
            val cleanupResult = settingsRepository.deleteUserData(userKey)
            if (cleanupResult is ResultWithError.Failure) {
                logger.e(TAG, "Settings cleanup failed before logout: ${cleanupResult.error}")
            }
        }
        return authRepository.logout().mapError { error ->
            logger.e(TAG, "Repository logout failed: $error")
            error.toUseCaseError()
        }
    }
}
