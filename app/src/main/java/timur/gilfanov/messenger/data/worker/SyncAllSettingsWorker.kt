package timur.gilfanov.messenger.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.settings.SyncAllPendingSettingsError
import timur.gilfanov.messenger.domain.usecase.settings.SyncAllPendingSettingsUseCase
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

    override suspend fun doWork(): Result = syncAllPendingSettings().fold(
        onSuccess = { Result.success() },
        onFailure = { error ->
            when (error) {
                SyncAllPendingSettingsError.IdentityNotAvailable -> {
                    logger.e(TAG, "Identity not available for sync")
                    Result.retry()
                }
                is SyncAllPendingSettingsError.LocalOperationFailed -> {
                    handleLocalError(error.error)
                }
                is SyncAllPendingSettingsError.RemoteSyncFailed -> {
                    handleRemoteError(error.error)
                }
            }
        },
    )

    private fun handleLocalError(error: LocalStorageError): Result = when (error) {
        LocalStorageError.TemporarilyUnavailable -> {
            logger.w(TAG, "Local storage temporarily unavailable")
            Result.retry()
        }
        LocalStorageError.StorageFull,
        LocalStorageError.AccessDenied,
        LocalStorageError.ReadOnly,
        LocalStorageError.Corrupted,
        -> {
            logger.e(TAG, "Permanent local storage error: $error")
            Result.failure()
        }
        is LocalStorageError.UnknownError -> {
            logger.e(TAG, "Unknown local storage error", error.cause)
            Result.failure()
        }
    }

    private fun handleRemoteError(error: RemoteError): Result = when (error) {
        RemoteError.Unauthenticated,
        RemoteError.InsufficientPermissions,
        -> {
            logger.e(TAG, "Authentication error: $error")
            Result.failure()
        }
        is RemoteError.Failed -> when (error) {
            RemoteError.Failed.NetworkNotAvailable,
            RemoteError.Failed.ServiceDown,
            -> {
                logger.w(TAG, "Transient remote error: $error")
                Result.retry()
            }
            is RemoteError.Failed.Cooldown -> {
                logger.e(TAG, "Remote error - cooldown: ${error.remaining}")
                Result.failure()
            }
            is RemoteError.Failed.UnknownServiceError -> {
                logger.e(TAG, "Remote error - unknown service error: ${error.cause}")
                Result.failure()
            }
        }
        is RemoteError.UnknownStatus -> when (error) {
            RemoteError.UnknownStatus.ServiceTimeout -> {
                logger.w(TAG, "Service timeout")
                Result.retry()
            }
        }
    }
}
