package timur.gilfanov.messenger.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID
import timur.gilfanov.messenger.data.repository.SettingsRepositoryImpl
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UserId
import timur.gilfanov.messenger.util.Logger

@HiltWorker
class SyncSettingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SettingsRepositoryImpl,
    private val logger: Logger,
) : CoroutineWorker(context, params) {

    @Suppress("ReturnCount")
    override suspend fun doWork(): Result {
        val userId =
            UserId(UUID.fromString(inputData.getString(KEY_USER_ID) ?: return Result.failure()))
        val settingKeyString = inputData.getString(KEY_SETTING_KEY) ?: return Result.failure()

        val settingKey = SettingKey.fromKey(settingKeyString)
        if (settingKey == null) {
            logger.w(TAG, "Unknown setting key from WorkManager: $settingKeyString")
            return Result.failure()
        }

        return when (repository.syncSetting(userId, settingKey)) {
            is timur.gilfanov.messenger.domain.entity.ResultWithError.Success -> Result.success()
            is timur.gilfanov.messenger.domain.entity.ResultWithError.Failure -> Result.retry()
        }
    }

    companion object {
        private const val TAG = "SyncSettingWorker"
        const val KEY_USER_ID = "user_id"
        const val KEY_SETTING_KEY = "setting_key"
    }
}
