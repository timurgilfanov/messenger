package timur.gilfanov.messenger.domain.usecase.user

import kotlinx.coroutines.flow.first
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SettingsRepository
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.util.Logger

/**
 * Synchronizes all pending setting updates for the active user.
 *
 * Collapses repository errors into [SettingsSyncOutcome] to indicate whether
 * the operation succeeded, failed permanently, or should be retried.
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
            settingsRepository.syncAllPendingSettings(identity).fold(
                onSuccess = { SettingsSyncOutcome.Success },
                onFailure = { error -> error.toSyncOutcome() },
            )
        },
        onFailure = {
            logger.e(TAG, "Unable to resolve identity for syncAllPending")
            SettingsSyncOutcome.Failure
        },
    )

    private fun SyncAllSettingsRepositoryError.toSyncOutcome(): SettingsSyncOutcome {
        var cause: Throwable? = null
        return when (this) {
            is SyncAllSettingsRepositoryError.LocalStorageError -> when (this) {
                SyncAllSettingsRepositoryError.LocalStorageError.TemporarilyUnavailable ->
                    SettingsSyncOutcome.Retry

                SyncAllSettingsRepositoryError.LocalStorageError.StorageFull,
                SyncAllSettingsRepositoryError.LocalStorageError.AccessDenied,
                SyncAllSettingsRepositoryError.LocalStorageError.ReadOnly,
                SyncAllSettingsRepositoryError.LocalStorageError.Corrupted,
                -> SettingsSyncOutcome.Failure
                is SyncAllSettingsRepositoryError.LocalStorageError.UnknownError,
                -> {
                    cause = this.cause
                    SettingsSyncOutcome.Failure
                }
            }

            is SyncAllSettingsRepositoryError.RemoteSyncFailed -> when (val err = error) {
                RepositoryError.Unauthenticated,
                RepositoryError.InsufficientPermissions,
                -> SettingsSyncOutcome.Failure

                is RepositoryError.Failed -> when (err) {
                    RepositoryError.Failed.NetworkNotAvailable,
                    RepositoryError.Failed.ServiceDown,
                    -> SettingsSyncOutcome.Retry

                    is RepositoryError.Failed.Cooldown,
                    RepositoryError.Failed.UnknownServiceError,
                    -> SettingsSyncOutcome.Failure
                }

                is RepositoryError.UnknownStatus -> when (err) {
                    RepositoryError.UnknownStatus.ServiceTimeout -> SettingsSyncOutcome.Retry
                }
            }
        }.also { outcome ->
            val message = "Repository syncAllPendingSettings failed: $this -> $outcome"
            if (cause != null) {
                logger.e(TAG, message, cause)
            } else {
                logger.e(TAG, message)
            }
        }
    }
}
