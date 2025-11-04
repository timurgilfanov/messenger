package timur.gilfanov.messenger.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timur.gilfanov.messenger.data.repository.SettingsRepositoryImpl

@HiltWorker
class SyncAllSettingsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SettingsRepositoryImpl,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (repository.syncAllPendingSettings()) {
        is SyncOutcome.Success -> Result.success()
        is SyncOutcome.Retry -> Result.retry()
        is SyncOutcome.Failure -> Result.failure()
    }
}
