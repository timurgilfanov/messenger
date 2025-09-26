package timur.gilfanov.messenger.debug

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timur.gilfanov.messenger.BuildConfig
import timur.gilfanov.messenger.MainActivity
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSource
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.util.Logger

/**
 * Debug-only activity lifecycle callbacks to capture intent extras and initialize debug features.
 * This allows us to detect debug scenario overrides passed via Android Studio
 * run configurations or adb commands, and ensures initialization happens after intent capture.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught") // Debug code
class DebugActivityLifecycleCallbacks(
    private val debugDataRepository: DebugDataRepository,
    private val debugNotificationService: DebugNotificationService,
    private val localDebugDataSource: LocalDebugDataSource,
    private val applicationScope: CoroutineScope,
    private val logger: Logger,
) : Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "DebugLifecycleCallbacks"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }

    private var isInitialized = false

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is MainActivity && !isInitialized) {
            // Capture debug scenario from intent FIRST
            val scenario = activity.intent?.getStringExtra("debug_data_scenario")
            if (scenario != null) {
                logger.d(TAG, "Captured debug data scenario from intent: $scenario")
                DebugIntentHolder.debugDataScenario = scenario
            }

            // Request notification permission for debug builds (Android 13+)
            requestNotificationPermissionIfNeeded(activity)

            // Now initialize debug features with correct scenario
            // Only initialize debug features for mock flavor (fake data sources)
            @Suppress("KotlinConstantConditions")
            if (!BuildConfig.USE_REAL_REMOTE_DATA_SOURCES) {
                applicationScope.launch {
                    // If we have a debug scenario from intent, clear sync state first
                    // to prevent race condition with sync loop
                    if (scenario != null) {
                        logger.d(TAG, "Clearing sync state before initializing scenario: $scenario")
                        clearSyncStateForFreshStart()
                    }
                    initializeDebugFeatures()
                }
            } else {
                logger.d(TAG, "Using real data sources, skipping debug initialization")
            }

            isInitialized = true
        }
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) {
        // Clear intent data when MainActivity is destroyed
        if (activity is MainActivity) {
            DebugIntentHolder.clear()
        }
    }

    private suspend fun initializeDebugFeatures() {
        logger.d(TAG, "Initializing debug features...")

        val scenario = determineDataScenario()
        logger.d(TAG, "Using data scenario: ${scenario.name}")

        // Initialize debug data with determined scenario
        debugDataRepository.initializeWithScenario(scenario)

        // Show debug notification
        debugNotificationService.showPersistentDebugNotification(scenario)

        logger.d(TAG, "Debug features initialized successfully")
    }

    /**
     * Determine which data scenario to use based on multiple sources:
     * Priority order:
     * 1. Intent extra (from Android Studio run config or adb command)
     * 2. BuildConfig (set via gradle property: -PdataScenario=HEAVY)
     * 3. Saved preference in DataStore
     * 4. Default to STANDARD
     */
    private suspend fun determineDataScenario(): DataScenario {
        // Priority 1: Check intent extra from DebugIntentHolder
        val intentScenario = DebugIntentHolder.debugDataScenario?.let { scenarioName ->
            try {
                DataScenario.valueOf(scenarioName)
            } catch (e: IllegalArgumentException) {
                logger.w(TAG, "Invalid intent scenario: $scenarioName", e)
                null
            }
        }
        if (intentScenario != null) {
            logger.d(TAG, "Using scenario from intent: $intentScenario")
            return intentScenario
        }

        // Priority 2: Check BuildConfig (set at compile time via gradle property)
        val buildScenario = try {
            DataScenario.valueOf(BuildConfig.DEFAULT_DATA_SCENARIO)
        } catch (e: IllegalArgumentException) {
            logger.w(TAG, "Invalid BuildConfig scenario: ${BuildConfig.DEFAULT_DATA_SCENARIO}", e)
            null
        }

        // Priority 3: Check saved preference in DataStore
        val savedScenario = debugDataRepository.getSettings().let { settings ->
            when (settings) {
                is ResultWithError.Success -> settings.data.scenario
                is ResultWithError.Failure -> {
                    logger.w(TAG, "Failed to get saved scenario")
                    null
                }
            }
        }

        // Use BuildConfig if it's not the default, otherwise use saved preference
        return when {
            buildScenario != null && buildScenario != DataScenario.STANDARD -> {
                logger.d(TAG, "Using BuildConfig scenario: $buildScenario")
                buildScenario
            }
            savedScenario != null -> {
                logger.d(TAG, "Using saved scenario: $savedScenario")
                savedScenario
            }
            else -> {
                logger.d(TAG, "Using default scenario: STANDARD")
                DataScenario.STANDARD
            }
        }
    }

    /**
     * Request notification permission for Android 13+ debug builds.
     * This ensures debug notifications can be shown without manual permission grant.
     */
    private fun requestNotificationPermissionIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission && activity is ComponentActivity) {
                logger.d(TAG, "Requesting notification permission for debug build")
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION,
                )
            } else if (hasPermission) {
                logger.d(TAG, "Notification permission already granted")
            }
        } else {
            logger.d(TAG, "Android < 13, notification permission not required")
        }
    }

    /**
     * Clear sync state to ensure fresh sync when launching with a debug scenario.
     * This prevents race condition where sync loop uses cached timestamp that's newer
     * than the regenerated data timestamps.
     */
    private suspend fun clearSyncStateForFreshStart() {
        logger.d(TAG, "Clearing local cache and sync timestamp for fresh start")
        val resultChats = localDebugDataSource.deleteAllChats()
        if (resultChats is ResultWithError.Failure) {
            logger.w(TAG, "Failed to clear chats: ${resultChats.error}")
        }
        val resultMessages = localDebugDataSource.deleteAllMessages()
        if (resultMessages is ResultWithError.Failure) {
            logger.w(TAG, "Failed to clear messages: ${resultMessages.error}")
        }
        val resultSyncTimestamp = localDebugDataSource.clearSyncTimestamp()
        if (resultSyncTimestamp is ResultWithError.Failure) {
            logger.w(TAG, "Failed to clear sync timestamp: ${resultSyncTimestamp.error}")
        }

        if (resultChats is ResultWithError.Success &&
            resultMessages is ResultWithError.Success &&
            resultSyncTimestamp is ResultWithError.Success
        ) {
            logger.d(TAG, "Sync state cleared successfully")
        }
    }
}
