package timur.gilfanov.messenger

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timur.gilfanov.messenger.data.repository.SettingsSyncScheduler

@HiltAndroidApp
class MessengerApplication :
    Application(),
    Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsSyncScheduler: SettingsSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        settingsSyncScheduler.schedulePeriodicSync()
    }
}
