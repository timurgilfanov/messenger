package timur.gilfanov.messenger.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.domain.usecase.user.SettingsSyncOutcome
import timur.gilfanov.messenger.domain.usecase.user.SyncSettingUseCase
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

        return when (syncSetting(userId, settingKey)) {
            SettingsSyncOutcome.Success -> Result.success()
            SettingsSyncOutcome.Retry -> Result.retry()
            SettingsSyncOutcome.Failure -> Result.failure()
        }
    }

    companion object {
        private const val TAG = "SyncSettingWorker"
        const val KEY_USER_ID = "user_id"
        const val KEY_SETTING_KEY = "setting_key"
    }
}
