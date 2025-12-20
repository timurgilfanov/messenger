package timur.gilfanov.messenger.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID
import timur.gilfanov.messenger.domain.entity.fold
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.usecase.profile.repository.RepositoryError
import timur.gilfanov.messenger.domain.usecase.settings.SyncSettingError
import timur.gilfanov.messenger.domain.usecase.settings.SyncSettingUseCase
import timur.gilfanov.messenger.domain.usecase.settings.repository.SyncSettingRepositoryError
import timur.gilfanov.messenger.util.Logger

@HiltWorker
class SyncSettingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncSetting: SyncSettingUseCase,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    @Suppress("ReturnCount")
    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            logger.w(
                TAG,
                "Max retry attempts ($MAX_RETRY_ATTEMPTS) reached for setting sync. " +
                    "Giving up - periodic sync will retry later.",
            )
            return Result.failure()
        }

        val userIdString = inputData.getString(KEY_USER_ID)
        if (userIdString == null) {
            logger.w(TAG, "Missing user_id in WorkManager input data")
            return Result.failure()
        }

        val userId = try {
            UserId(UUID.fromString(userIdString))
        } catch (_: IllegalArgumentException) {
            logger.w(TAG, "Invalid user_id format: $userIdString")
            return Result.failure()
        }

        val settingKeyString = inputData.getString(KEY_SETTING_KEY)
        if (settingKeyString == null) {
            logger.w(TAG, "Missing setting_key in WorkManager input data")
            return Result.failure()
        }

        val settingKey = SettingKey.fromKey(settingKeyString)
        if (settingKey == null) {
            logger.w(TAG, "Unknown setting key from WorkManager: $settingKeyString")
            return Result.failure()
        }

        return syncSetting(userId, settingKey).fold(
            onSuccess = {
                if (runAttemptCount > 0) {
                    logger.i(TAG, "Setting sync succeeded after $runAttemptCount retries")
                }
                Result.success()
            },
            onFailure = { error ->
                when (error) {
                    SyncSettingError.IdentityNotAvailable -> {
                        logger.e(TAG, "Identity not available for user ${userId.id}")
                        Result.retry()
                    }
                    is SyncSettingError.SyncFailed -> {
                        handleSyncError(error.error)
                    }
                }
            },
        )
    }

    private fun handleSyncError(error: SyncSettingRepositoryError): Result = when (error) {
        is SyncSettingRepositoryError.LocalStorageError -> {
            when (error) {
                SyncSettingRepositoryError.LocalStorageError.TemporarilyUnavailable -> {
                    logger.w(TAG, "Local storage temporarily unavailable")
                    Result.retry()
                }
                SyncSettingRepositoryError.LocalStorageError.StorageFull,
                SyncSettingRepositoryError.LocalStorageError.AccessDenied,
                SyncSettingRepositoryError.LocalStorageError.ReadOnly,
                SyncSettingRepositoryError.LocalStorageError.Corrupted,
                -> {
                    logger.e(TAG, "Permanent local storage error: $error")
                    Result.failure()
                }
                is SyncSettingRepositoryError.LocalStorageError.UnknownError -> {
                    logger.e(TAG, "Unknown local storage error", error.cause)
                    Result.failure()
                }
            }
        }
        is SyncSettingRepositoryError.RemoteSyncFailed -> {
            handleRemoteError(error.error)
        }
        SyncSettingRepositoryError.SettingNotFound -> {
            logger.e(TAG, "Setting not found")
            Result.failure()
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
            is RepositoryError.Failed.Cooldown -> {
                logger.e(TAG, "Remote error - cooldown: ${error.remaining}")
                Result.failure()
            }
            is RepositoryError.Failed.UnknownServiceError -> {
                logger.e(TAG, "Remote error - unknown service error: ${error.cause}")
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

    companion object {
        private const val TAG = "SyncSettingWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
        const val KEY_USER_ID = "user_id"
        const val KEY_SETTING_KEY = "setting_key"
    }
}
