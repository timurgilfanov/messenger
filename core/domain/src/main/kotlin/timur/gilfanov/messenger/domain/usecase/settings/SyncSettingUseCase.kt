package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.toUserScopeKey
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Synchronizes a single user setting with the remote backend.
 */
class SyncSettingUseCase(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "SyncSettingUseCase"
    }

    suspend operator fun invoke(key: SettingKey): ResultWithError<Unit, SyncSettingError> =
        when (val state = authRepository.authState.first()) {
            is AuthState.Authenticated ->
                settingsRepository.syncSetting(state.session.toUserScopeKey(), key).fold(
                    onSuccess = { ResultWithError.Success(Unit) },
                    onFailure = { error ->
                        logger.e(
                            TAG,
                            "Repository syncSetting failed for key ${key.key}: $error",
                        )
                        ResultWithError.Failure(error.toUseCaseError())
                    },
                )

            AuthState.Unauthenticated -> {
                logger.e(TAG, "Unable to resolve identity for syncSetting")
                ResultWithError.Failure(SyncSettingError.IdentityNotAvailable)
            }
        }
}
