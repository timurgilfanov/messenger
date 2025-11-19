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
        is timur.gilfanov.messenger.domain.entity.ResultWithError.Success -> Result.success()
        is timur.gilfanov.messenger.domain.entity.ResultWithError.Failure -> Result.retry()
    }
}
