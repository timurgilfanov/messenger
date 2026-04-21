package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Synchronizes all pending setting updates for the active user.
 */
class SyncAllPendingSettingsUseCase(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "SyncAllPendingSettingsUseCase"
        private const val AUTH_STATE_TIMEOUT_MS = 5_000L
    }

    suspend operator fun invoke(): ResultWithError<Unit, SyncAllPendingSettingsError> {
        val state = withTimeoutOrNull(AUTH_STATE_TIMEOUT_MS) {
            authRepository.authState.first()
        } ?: run {
            logger.e(TAG, "Timed out waiting for auth state in syncAllPending")
            return ResultWithError.Failure(SyncAllPendingSettingsError.IdentityNotAvailable)
        }
        return when (state) {
            is AuthState.Authenticated ->
                settingsRepository.syncAllPendingSettings(state.session.toUserScopeKey()).fold(
                    onSuccess = { ResultWithError.Success(Unit) },
                    onFailure = { error ->
                        logger.e(TAG, "Repository syncAllPendingSettings failed: $error")
                        ResultWithError.Failure(error.toUseCaseError())
                    },
                )

            AuthState.Unauthenticated -> {
                logger.e(TAG, "Unable to resolve identity for syncAllPending")
                ResultWithError.Failure(SyncAllPendingSettingsError.IdentityNotAvailable)
            }
        }
    }
}
