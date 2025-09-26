package timur.gilfanov.messenger.debug

/**
 * Holds debug intent data captured from MainActivity launches.
 * This allows the debug application class to access intent extras
 * that were passed via Android Studio run configurations or adb commands.
 *
 * This is in debug source set only and won't exist in release builds.
 */
object DebugIntentHolder {
    /**
     * Debug data scenario passed via intent extra "debug_data_scenario"
     */
    var debugDataScenario: String? = null

    /**
     * Clear all captured intent data
     */
    fun clear() {
        debugDataScenario = null
    }
}
