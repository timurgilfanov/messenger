package timur.gilfanov.messenger.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.usecase.user.SyncAllPendingSettingsError
import timur.gilfanov.messenger.domain.usecase.user.SyncAllPendingSettingsUseCase
import timur.gilfanov.messenger.domain.usecase.user.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.user.repository.SyncAllSettingsRepositoryError
import timur.gilfanov.messenger.util.Logger

@HiltWorker
class SyncAllSettingsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncAllPendingSettings: SyncAllPendingSettingsUseCase,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncAllSettingsWorker"
    }

    override suspend fun doWork(): Result = when (val outcome = syncAllPendingSettings()) {
        is ResultWithError.Success -> {
            Result.success()
        }
        is ResultWithError.Failure -> {
            when (val error = outcome.error) {
                SyncAllPendingSettingsError.IdentityNotAvailable -> {
                    logger.e(TAG, "Identity not available for sync")
                    Result.retry()
                }
                is SyncAllPendingSettingsError.SyncFailed -> {
                    handleSyncError(error.error)
                }
            }
        }
    }

    private fun handleSyncError(error: SyncAllSettingsRepositoryError): Result = when (error) {
        is SyncAllSettingsRepositoryError.LocalStorageError -> {
            when (error) {
                SyncAllSettingsRepositoryError.LocalStorageError.TemporarilyUnavailable -> {
                    logger.w(TAG, "Local storage temporarily unavailable")
                    Result.retry()
                }
                SyncAllSettingsRepositoryError.LocalStorageError.StorageFull,
                SyncAllSettingsRepositoryError.LocalStorageError.AccessDenied,
                SyncAllSettingsRepositoryError.LocalStorageError.ReadOnly,
                SyncAllSettingsRepositoryError.LocalStorageError.Corrupted,
                -> {
                    logger.e(TAG, "Permanent local storage error: $error")
                    Result.failure()
                }
                is SyncAllSettingsRepositoryError.LocalStorageError.UnknownError -> {
                    logger.e(TAG, "Unknown local storage error", error.cause)
                    Result.failure()
                }
            }
        }
        is SyncAllSettingsRepositoryError.RemoteSyncFailed -> {
            handleRemoteError(error.error)
        }
    }

    private fun handleRemoteError(error: RepositoryError): Result = when (error) {
        RepositoryError.Unauthenticated,
        RepositoryError.InsufficientPermissions,
        -> {
            logger.e(TAG, "Authentication error: $error")
            Result.failure()
        }
        is RepositoryError.Failed -> when (error) {
            RepositoryError.Failed.NetworkNotAvailable,
            RepositoryError.Failed.ServiceDown,
            -> {
                logger.w(TAG, "Transient remote error: $error")
                Result.retry()
            }
            is RepositoryError.Failed.Cooldown,
            RepositoryError.Failed.UnknownServiceError,
            -> {
                logger.e(TAG, "Remote error: $error")
                Result.failure()
            }
        }
        is RepositoryError.UnknownStatus -> when (error) {
            is RepositoryError.UnknownStatus.ServiceTimeout -> {
                logger.w(TAG, "Service timeout")
                Result.retry()
            }
        }
    }
}
