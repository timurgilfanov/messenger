package timur.gilfanov.messenger.domain.usecase.user

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.fold
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
class SyncSettingUseCase(
    private val identityRepository: IdentityRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(userId: UserId, key: SettingKey): SettingsSyncOutcome =
        identityRepository.getIdentity(userId).fold(
            onSuccess = { identity ->
                when (val result = settingsRepository.syncSetting(identity, key)) {
                    is ResultWithError.Success -> SettingsSyncOutcome.Success
                    is ResultWithError.Failure -> result.error.toSyncOutcome()
                }
            },
            onFailure = {
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
