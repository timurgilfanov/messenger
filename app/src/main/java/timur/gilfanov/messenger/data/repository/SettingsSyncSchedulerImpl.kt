package timur.gilfanov.messenger.data.repository

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timur.gilfanov.messenger.data.worker.SyncAllSettingsWorker
import timur.gilfanov.messenger.data.worker.SyncSettingWorker
import timur.gilfanov.messenger.domain.entity.user.SettingKey
import timur.gilfanov.messenger.domain.entity.user.UserId

@Singleton
class SettingsSyncSchedulerImpl @Inject constructor(private val workManager: WorkManager) :
    SettingsSyncScheduler {

    companion object {
        private const val DEBOUNCE_DELAY_MS = 500L
        private const val BACKOFF_DELAY_SECONDS = 5L
        private const val PERIODIC_SYNC_WORK_NAME = "sync_all_settings_periodic"
    }

    override fun scheduleSettingSync(userId: UserId, key: SettingKey) {
        val userIdString = userId.id.toString()
        val workRequest = OneTimeWorkRequestBuilder<SyncSettingWorker>()
            .setInputData(
                workDataOf(
                    SyncSettingWorker.KEY_USER_ID to userIdString,
                    SyncSettingWorker.KEY_SETTING_KEY to key.key,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS,
            )
            .setInitialDelay(DEBOUNCE_DELAY_MS, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "sync_setting_${userIdString}_${key.key}",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest,
        )
    }

    override fun schedulePeriodicSync() {
        val workRequest = PeriodicWorkRequestBuilder<SyncAllSettingsWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        ).build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest,
        )
    }
}
