package timur.gilfanov.messenger.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

        try {
            coroutineScope.launch {
                try {
                    logger.d(TAG, "Regenerating debug data...")
                    debugDataRepository.regenerateData()

                    // Update notification with current scenario
                    val currentSettings = debugDataRepository.settings.first()
                    debugNotificationService.updateNotification(currentSettings.scenario)

                    logger.d(TAG, "Debug data regeneration completed")
                } catch (e: IOException) {
                    logger.e(TAG, "Failed to regenerate debug data - IO error", e)
                } catch (e: CancellationException) {
                    throw e // Re-throw cancellation
                } catch (e: SecurityException) {
                    logger.e(TAG, "Failed to regenerate debug data - permission denied", e)
                } catch (e: IllegalArgumentException) {
                    logger.e(TAG, "Failed to regenerate debug data - invalid arguments", e)
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (e: IOException) {
            logger.e(TAG, "Error handling regenerate data - IO error", e)
            pendingResult.finish()
        } catch (e: SecurityException) {
            logger.e(TAG, "Error handling regenerate data - permission denied", e)
            pendingResult.finish()
        } catch (e: IllegalArgumentException) {
            logger.e(TAG, "Error handling regenerate data - invalid arguments", e)
            pendingResult.finish()
        }
    }
}
