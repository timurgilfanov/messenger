package timur.gilfanov.messenger.debug

import android.content.Context
import android.os.Build
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import timur.gilfanov.messenger.MessengerApplication

/**
 * Debug-only Application class that extends the main MessengerApplication.
 * This class handles debug data initialization and provides debug-specific features
 * like sample data population and debug notifications.
 *
 * This is only used in debug builds and won't exist in release builds.
 * Note: Does not need @HiltAndroidApp since it inherits from MessengerApplication.
 */
class DebugMessengerApplication : MessengerApplication() {

    private lateinit var debugDataRepository: DebugDataRepository
    private lateinit var debugNotificationService: DebugNotificationService
    private lateinit var localDebugDataSource:
        timur.gilfanov.messenger.data.source.local.LocalDebugDataSource
    private lateinit var logger: timur.gilfanov.messenger.util.Logger

    // Debug broadcast receiver for notification actions
    private var debugBroadcastReceiver: DebugBroadcastReceiver? = null

    // Application-scoped coroutine scope for debug operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "DebugMessengerApp"
    }

    override fun onCreate() {
        super.onCreate()

        // Access Hilt dependencies through EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            this,
            DebugApplicationEntryPoint::class.java,
        )
        logger = entryPoint.logger()
        debugDataRepository = entryPoint.debugDataRepository()
        debugNotificationService = entryPoint.debugNotificationService()
        localDebugDataSource = entryPoint.localDebugDataSource()

        logger.d(TAG, "Debug MessengerApplication starting")

        // Register debug broadcast receiver for notification actions
        debugBroadcastReceiver = DebugBroadcastReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                debugBroadcastReceiver,
                DebugBroadcastReceiver.createIntentFilter(),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            registerReceiver(debugBroadcastReceiver, DebugBroadcastReceiver.createIntentFilter())
        }
        logger.d(TAG, "Debug broadcast receiver registered")

        // Register activity lifecycle callbacks to handle debug initialization
        registerActivityLifecycleCallbacks(
            DebugActivityLifecycleCallbacks(
                debugDataRepository,
                debugNotificationService,
                localDebugDataSource,
                applicationScope,
                logger,
            ),
        )
    }

    override fun onTerminate() {
        super.onTerminate()

        // Unregister debug broadcast receiver
        debugBroadcastReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
                logger.d(TAG, "Debug broadcast receiver unregistered")
            } catch (e: IllegalArgumentException) {
                logger.w(TAG, "Debug broadcast receiver was not registered", e)
            }
            debugBroadcastReceiver = null
        }
    }
}
