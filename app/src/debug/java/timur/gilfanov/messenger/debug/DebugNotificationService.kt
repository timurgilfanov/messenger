package timur.gilfanov.messenger.debug

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.util.Logger

@Singleton
class DebugNotificationService @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: Logger,
) {
    companion object {
        private const val TAG = "DebugNotificationService"
        private const val CHANNEL_ID = "debug_controls"
        private const val CHANNEL_NAME = "Debug Controls"
        private const val NOTIFICATION_ID = 9999

        // Action for regenerating data
        const val ACTION_REGENERATE_DATA = "debug_regenerate_data"
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    /**
     * Show persistent debug notification with current scenario info
     */
    fun showPersistentDebugNotification(scenario: DataScenario) {
        if (!hasNotificationPermission()) {
            logger.w(TAG, "No notification permission, cannot show debug notification")
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Debug Mode: ${scenario.name}")
            .setContentText("${scenario.description} • ${scenario.chatCount} chats")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    buildString {
                        appendLine("Scenario: ${scenario.name}")
                        appendLine("Description: ${scenario.description}")
                        appendLine("Chats: ${scenario.chatCount}")
                        appendLine("Messages: ${scenario.messagesPerChat}")
                        appendLine()
                        append("Tap to open debug settings")
                    },
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Makes it persistent
            .setContentIntent(createOpenSettingsIntent())
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Regenerate",
                createRegenerateIntent(),
            )
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
            logger.d(TAG, "Debug notification shown for scenario: ${scenario.name}")
        } catch (e: SecurityException) {
            logger.e(TAG, "Failed to show debug notification due to permission", e)
        }
    }

    /**
     * Hide the debug notification
     */
    fun hideDebugNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        logger.d(TAG, "Debug notification hidden")
    }

    /**
     * Update notification with new scenario info
     */
    fun updateNotification(scenario: DataScenario) {
        showPersistentDebugNotification(scenario)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Debug controls for development"
                setShowBadge(false)
            }

            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE,
            ) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createOpenSettingsIntent(): PendingIntent {
        val intent = Intent(
            context,
            timur.gilfanov.messenger.debug.ui.DebugSettingsActivity::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createRegenerateIntent(): PendingIntent {
        // Create a broadcast intent that can be handled by the debug repository
        val intent = Intent(ACTION_REGENERATE_DATA).apply {
            setPackage(context.packageName)
        }

        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Check if notification permission is granted
     */
    private fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED
        } else {
            // Before API 33, notification permission was granted by default
            true
        }
}
