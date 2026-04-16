package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.first
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
    }

    suspend operator fun invoke(): ResultWithError<Unit, SyncAllPendingSettingsError> =
        when (val state = authRepository.authState.first { it !is AuthState.Loading }) {
            AuthState.Loading -> {
                logger.e(TAG, "Unable to resolve identity for syncAllPending")
                ResultWithError.Failure(SyncAllPendingSettingsError.IdentityNotAvailable)
            }

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
