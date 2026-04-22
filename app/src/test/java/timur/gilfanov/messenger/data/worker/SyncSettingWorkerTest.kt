package timur.gilfanov.messenger.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.settings.SettingKey
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.domain.usecase.settings.SyncSettingError
import timur.gilfanov.messenger.domain.usecase.settings.SyncSettingUseCase

@Category(timur.gilfanov.messenger.annotations.Unit::class)
@RunWith(RobolectricTestRunner::class)
class SyncSettingWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class SyncRecorder {
        var invocationCount: Int = 0
    }

    private fun recordingUseCase(
        recorder: SyncRecorder,
        result: ResultWithError<Unit, SyncSettingError> = ResultWithError.Success(Unit),
        beforeReturn: suspend () -> Unit = {},
    ): SyncSettingUseCase = object : SyncSettingUseCase {
        override suspend fun invoke(key: SettingKey): ResultWithError<Unit, SyncSettingError> {
            recorder.invocationCount++
            beforeReturn()
            return result
        }
    }

    private fun buildWorker(
        useCase: SyncSettingUseCase,
        settingKey: String? = SettingKey.UI_LANGUAGE.key,
    ): SyncSettingWorker {
        val builder = TestListenableWorkerBuilder<SyncSettingWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = SyncSettingWorker(
                        appContext,
                        workerParameters,
                        useCase,
                        NoOpLogger(),
                    )
                },
            )
        if (settingKey != null) {
            builder.setInputData(
                Data.Builder().putString(SyncSettingWorker.KEY_SETTING_KEY, settingKey).build(),
            )
        }
        return builder.build() as SyncSettingWorker
    }

    @Test
    fun `when isStopped before doWork then returns failure without invoking use case`() = runTest {
        val recorder = SyncRecorder()
        val worker = buildWorker(recordingUseCase(recorder))

        @Suppress("RestrictedApi")
        worker.stop(WorkInfo.STOP_REASON_CANCELLED_BY_APP)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(0, recorder.invocationCount)
    }

    @Test
    fun `when sync succeeds and worker not stopped then returns success`() = runTest {
        val recorder = SyncRecorder()
        val worker = buildWorker(recordingUseCase(recorder))

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, recorder.invocationCount)
    }

    @Test
    fun `when worker stopped during sync then success is discarded`() = runTest {
        val recorder = SyncRecorder()
        lateinit var worker: SyncSettingWorker
        worker = buildWorker(
            recordingUseCase(recorder) {
                @Suppress("RestrictedApi")
                worker.stop(WorkInfo.STOP_REASON_CANCELLED_BY_APP)
            },
        )

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(1, recorder.invocationCount)
    }

    @Test
    fun `when input data missing setting key then returns failure`() = runTest {
        val recorder = SyncRecorder()
        val worker = buildWorker(recordingUseCase(recorder), settingKey = null)

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(0, recorder.invocationCount)
    }
}
