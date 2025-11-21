package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.util.Logger

/**
 * Synchronizes all pending setting updates queued locally.
 *
 * Acts as a bridge between the repository and WorkManager by collapsing repository
 * errors into [SettingsSyncOutcome] so the worker can decide whether to retry or fail.
 */
class SyncAllPendingSettingsUseCase(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "SyncAllSettingsUseCase"
    }

    suspend operator fun invoke(): SettingsSyncOutcome = identityRepository.identity.first().fold(
        onSuccess = { identity ->
            when (val result = settingsRepository.syncAllPendingSettings(identity)) {
                is ResultWithError.Success -> SettingsSyncOutcome.Success
                is ResultWithError.Failure -> {
                    val outcome = result.error.toSyncOutcome()
                    logger.e(
                        TAG,
                        "Repository syncAllPendingSettings failed: ${result.error} -> $outcome",
                    )
                    outcome
                }
            }
        },
        onFailure = {
            logger.e(TAG, "Unable to resolve identity for syncAllPending")
            SettingsSyncOutcome.Failure
        },
    )

    private fun SyncAllSettingsRepositoryError.toSyncOutcome(): SettingsSyncOutcome = when (this) {
        is SyncAllSettingsRepositoryError.LocalStorageError -> when (this) {
            SyncAllSettingsRepositoryError.LocalStorageError.TemporarilyUnavailable ->
                SettingsSyncOutcome.Retry

            SyncAllSettingsRepositoryError.LocalStorageError.StorageFull,
            SyncAllSettingsRepositoryError.LocalStorageError.AccessDenied,
            SyncAllSettingsRepositoryError.LocalStorageError.ReadOnly,
            SyncAllSettingsRepositoryError.LocalStorageError.Corrupted,
            is SyncAllSettingsRepositoryError.LocalStorageError.UnknownError,
            ->
                SettingsSyncOutcome.Failure
        }

        is SyncAllSettingsRepositoryError.RemoteSyncFailed -> when (val err = error) {
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
