package timur.gilfanov.messenger.domain.usecase.settings

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.usecase.profile.IdentityRepository
import timur.gilfanov.messenger.domain.usecase.settings.repository.SettingsRepository
import timur.gilfanov.messenger.util.Logger

/**
 * Synchronizes a single user setting with the remote backend.
 */
class SyncSettingUseCase(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "SyncSettingUseCase"
    }

    suspend operator fun invoke(
        userId: UserId,
        key: SettingKey,
    ): ResultWithError<Unit, SyncSettingError> = identityRepository.getIdentity(userId).fold(
        onSuccess = { identity ->
            settingsRepository.syncSetting(identity, key).fold(
                onSuccess = { ResultWithError.Success(Unit) },
                onFailure = { error ->
                    logger.e(
                        TAG,
                        "Repository syncSetting failed for user ${userId.id} key ${key.key}: $error",
                    )
                    ResultWithError.Failure(error.toUseCaseError())
                },
            )
        },
        onFailure = {
            logger.e(TAG, "Unable to resolve identity for syncSetting user ${userId.id}")
            ResultWithError.Failure(SyncSettingError.IdentityNotAvailable)
        },
    )
}
