package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncSettingRepositoryError

/**
 * Synchronizes a single user setting with the remote backend.
 *
 * Exposes a WorkManager-friendly contract via [SettingsSyncOutcome] that hides
 * repository error taxonomies and centralizes the retry/failure strategy.
 */
class SyncSettingUseCase(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke(userId: UserId, key: SettingKey): SettingsSyncOutcome =
        when (val result = settingsRepository.syncSetting(userId, key)) {
            is ResultWithError.Success -> SettingsSyncOutcome.Success
            is ResultWithError.Failure -> result.error.toSyncOutcome()
        }

    private fun SyncSettingRepositoryError.toSyncOutcome(): SettingsSyncOutcome = when (this) {
        SyncSettingRepositoryError.SettingNotFound -> SettingsSyncOutcome.Failure
        is SyncSettingRepositoryError.LocalStorageError -> when (this) {
            SyncSettingRepositoryError.LocalStorageError.TemporarilyUnavailable,
            SyncSettingRepositoryError.LocalStorageError.StorageFull,
            SyncSettingRepositoryError.LocalStorageError.AccessDenied,
            SyncSettingRepositoryError.LocalStorageError.ReadOnly,
            is SyncSettingRepositoryError.LocalStorageError.UnknownError,
            -> SettingsSyncOutcome.Retry
            SyncSettingRepositoryError.LocalStorageError.Corrupted,
            -> SettingsSyncOutcome.Failure
        }
        is SyncSettingRepositoryError.RemoteSyncFailed -> when (error) {
            RepositoryError.Unauthenticated,
            RepositoryError.InsufficientPermissions,
            -> SettingsSyncOutcome.Failure
            is RepositoryError.Failed,
            is RepositoryError.UnknownStatus,
            -> SettingsSyncOutcome.Retry
        }
    }
}
