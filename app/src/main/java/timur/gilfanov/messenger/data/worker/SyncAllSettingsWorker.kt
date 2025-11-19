package timur.gilfanov.messenger.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timur.gilfanov.messenger.domain.usecase.user.SettingsSyncOutcome
import timur.gilfanov.messenger.domain.usecase.user.SyncAllPendingSettingsUseCase

@HiltWorker
class SyncAllSettingsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncAllPendingSettings: SyncAllPendingSettingsUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (syncAllPendingSettings()) {
        SettingsSyncOutcome.Success -> Result.success()
        SettingsSyncOutcome.Retry -> Result.retry()
        SettingsSyncOutcome.Failure -> Result.failure()
    }
}
