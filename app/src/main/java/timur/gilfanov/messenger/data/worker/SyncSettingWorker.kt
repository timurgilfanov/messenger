package timur.gilfanov.messenger.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID
import timur.gilfanov.messenger.data.repository.SettingsRepositoryImpl
import timur.gilfanov.messenger.domain.entity.user.UserId

@HiltWorker
class SyncSettingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SettingsRepositoryImpl,
) : CoroutineWorker(context, params) {

    @Suppress("ReturnCount")
    override suspend fun doWork(): Result {
        val userId =
            UserId(UUID.fromString(inputData.getString(KEY_USER_ID) ?: return Result.failure()))
        val settingKey = inputData.getString(KEY_SETTING_KEY) ?: return Result.failure()

        return when (repository.syncSetting(userId, settingKey)) {
            is SyncOutcome.Success -> Result.success()
            is SyncOutcome.Retry -> Result.retry()
            is SyncOutcome.Failure -> Result.failure()
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_SETTING_KEY = "setting_key"
    }
}

sealed class SyncOutcome {
    data object Success : SyncOutcome()
    data object Retry : SyncOutcome()
    data object Failure : SyncOutcome()
}
