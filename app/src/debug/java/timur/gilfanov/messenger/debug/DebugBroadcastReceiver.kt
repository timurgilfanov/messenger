package timur.gilfanov.messenger.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.util.Logger

/**
 * Debug-only broadcast receiver to handle actions from debug notifications.
 * This allows users to trigger debug actions (like data regeneration)
 * directly from the persistent debug notification.
 */
@AndroidEntryPoint
class DebugBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var debugDataRepository: DebugDataRepository

    @Inject
    lateinit var debugNotificationService: DebugNotificationService

    @Inject
    lateinit var logger: Logger

    @Inject
    @Named("debug")
    lateinit var coroutineScope: CoroutineScope

    companion object {
        private const val TAG = "DebugBroadcastReceiver"

        /**
         * Create intent filter for debug broadcast actions
         */
        fun createIntentFilter(): IntentFilter = IntentFilter().apply {
            addAction(DebugNotificationService.ACTION_REGENERATE_DATA)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        logger.d(TAG, "Received debug broadcast: ${intent.action}")

        when (intent.action) {
            DebugNotificationService.ACTION_REGENERATE_DATA -> {
                handleRegenerateData()
            }
            else -> {
                logger.w(TAG, "Unknown debug action: ${intent.action}")
            }
        }
    }

    private fun handleRegenerateData() {
        logger.d(TAG, "Handling regenerate data request")

        // Use goAsync() for background operations in broadcast receiver
        val pendingResult = goAsync()

        coroutineScope.launch(Dispatchers.IO) {
            try {
                logger.d(TAG, "Regenerating debug data...")
                when (val result = debugDataRepository.regenerateData()) {
                    is ResultWithError.Success -> {
                        // Update notification with current scenario
                        val currentSettings = debugDataRepository.settings.first()
                        debugNotificationService.updateNotification(currentSettings.scenario)
                        logger.d(TAG, "Debug data regeneration completed")
                    }
                    is ResultWithError.Failure -> {
                        logger.w(TAG, "Debug data regeneration failed: ${result.error}")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
