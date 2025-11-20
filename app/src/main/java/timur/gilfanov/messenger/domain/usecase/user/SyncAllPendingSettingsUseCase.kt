package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncAllSettingsRepositoryError

/**
 * Synchronizes all pending setting updates queued locally.
 *
 * Acts as a bridge between the repository and WorkManager by collapsing repository
 * errors into [SettingsSyncOutcome] so the worker can decide whether to retry or fail.
 */
class SyncAllPendingSettingsUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(): SettingsSyncOutcome =
        when (val result = settingsRepository.syncAllPendingSettings()) {
            is ResultWithError.Success -> SettingsSyncOutcome.Success
            is ResultWithError.Failure -> result.error.toSyncOutcome()
        }

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
