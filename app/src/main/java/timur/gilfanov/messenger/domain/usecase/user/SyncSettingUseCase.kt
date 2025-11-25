package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncSettingRepositoryError
import timur.gilfanov.messenger.util.Logger

/**
 * Synchronizes a single user setting with the remote backend.
 *
 * Collapses repository errors into [SettingsSyncOutcome] to indicate whether
 * the operation succeeded, failed permanently, or should be retried.
 */
class SyncSettingUseCase(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "SyncSettingUseCase"
    }

    suspend operator fun invoke(userId: UserId, key: SettingKey): SettingsSyncOutcome =
        identityRepository.getIdentity(userId).fold(
            onSuccess = { identity ->
                when (val result = settingsRepository.syncSetting(identity, key)) {
                    is ResultWithError.Success -> SettingsSyncOutcome.Success
                    is ResultWithError.Failure -> {
                        val outcome = result.error.toSyncOutcome()
                        logger.e(
                            TAG,
                            "Repository syncSetting failed for user ${userId.id} key ${key.key}: " +
                                "${result.error} -> $outcome",
                        )
                        outcome
                    }
                }
            },
            onFailure = {
                logger.e(
                    TAG,
                    "Unable to resolve identity for syncSetting user ${userId.id}",
                )
                SettingsSyncOutcome.Failure
            },
        )

    private fun SyncSettingRepositoryError.toSyncOutcome(): SettingsSyncOutcome = when (this) {
        SyncSettingRepositoryError.SettingNotFound -> SettingsSyncOutcome.Failure

        is SyncSettingRepositoryError.LocalStorageError -> when (this) {
            SyncSettingRepositoryError.LocalStorageError.TemporarilyUnavailable ->
                SettingsSyncOutcome.Retry

            SyncSettingRepositoryError.LocalStorageError.StorageFull,
            SyncSettingRepositoryError.LocalStorageError.AccessDenied,
            SyncSettingRepositoryError.LocalStorageError.ReadOnly,
            SyncSettingRepositoryError.LocalStorageError.Corrupted,
            is SyncSettingRepositoryError.LocalStorageError.UnknownError,
            ->
                SettingsSyncOutcome.Failure
        }

        is SyncSettingRepositoryError.RemoteSyncFailed -> when (val err = error) {
            RepositoryError.Unauthenticated,
            RepositoryError.InsufficientPermissions,
            ->
                SettingsSyncOutcome.Failure

            is RepositoryError.Failed -> when (err) {
                RepositoryError.Failed.NetworkNotAvailable,
                RepositoryError.Failed.ServiceDown,
                ->
                    SettingsSyncOutcome.Retry

                is RepositoryError.Failed.Cooldown,
                RepositoryError.Failed.UnknownServiceError,
                ->
                    SettingsSyncOutcome.Failure
            }

            is RepositoryError.UnknownStatus -> when (err) {
                RepositoryError.UnknownStatus.ServiceTimeout -> SettingsSyncOutcome.Retry
            }
        }
    }
}
