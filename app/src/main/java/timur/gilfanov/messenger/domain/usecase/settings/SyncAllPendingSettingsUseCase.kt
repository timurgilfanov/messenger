package timur.gilfanov.messenger.domain.usecase.settings

import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Synchronizes all pending setting updates for the active user.
 */
class SyncAllPendingSettingsUseCase(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "SyncAllPendingSettingsUseCase"
    }

    suspend operator fun invoke(): ResultWithError<Unit, SyncAllPendingSettingsError> =
        identityRepository.identity.first().fold(
            onSuccess = { identity ->
                settingsRepository.syncAllPendingSettings(identity).fold(
                    onSuccess = { ResultWithError.Success(Unit) },
                    onFailure = { error ->
                        logger.e(TAG, "Repository syncAllPendingSettings failed: $error")
                        ResultWithError.Failure(SyncAllPendingSettingsError.SyncFailed(error))
                    },
                )
            },
            onFailure = {
                logger.e(TAG, "Unable to resolve identity for syncAllPending")
                ResultWithError.Failure(SyncAllPendingSettingsError.IdentityNotAvailable)
            },
        )
}
