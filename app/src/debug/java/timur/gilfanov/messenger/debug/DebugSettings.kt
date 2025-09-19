package timur.gilfanov.messenger.debug

import androidx.datastore.preferences.core.Preferences
import kotlin.time.Instant
import timur.gilfanov.messenger.debug.datastore.DebugPreferences

/**
 * Data class representing current debug settings.
 * Used to hold configuration loaded from DataStore.
 */
data class DebugSettings(
    val useSampleData: Boolean = true,
    val scenario: DataScenario = DataScenario.STANDARD,
    val autoActivity: Boolean = false,
    val showNotification: Boolean = true,
    val lastGeneration: Long = 0L,
) {
    companion object {
        /**
         * Create DebugSettings from DataStore Preferences
         */
        fun fromPreferences(preferences: Preferences): DebugSettings {
            val scenarioName =
                preferences[DebugPreferences.DATA_SCENARIO] ?: DataScenario.STANDARD.name
            val scenario = DataScenario.fromString(scenarioName) ?: DataScenario.STANDARD

            return DebugSettings(
                useSampleData = preferences[DebugPreferences.USE_SAMPLE_DATA] ?: true,
                scenario = scenario,
                autoActivity = preferences[DebugPreferences.AUTO_ACTIVITY_ENABLED] ?: false,
                showNotification = preferences[DebugPreferences.SHOW_DEBUG_NOTIFICATION] ?: true,
                lastGeneration = preferences[DebugPreferences.LAST_DATA_GENERATION] ?: 0L,
            )
        }
    }

    /**
     * Write these settings to DataStore Preferences
     */
    fun toPreferences(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        preferences[DebugPreferences.USE_SAMPLE_DATA] = useSampleData
        preferences[DebugPreferences.DATA_SCENARIO] = scenario.name
        preferences[DebugPreferences.AUTO_ACTIVITY_ENABLED] = autoActivity
        preferences[DebugPreferences.SHOW_DEBUG_NOTIFICATION] = showNotification
        preferences[DebugPreferences.LAST_DATA_GENERATION] = lastGeneration
    }

    /**
     * Whether data was generated recently (within last hour)
     */
    val wasGeneratedRecently: Boolean
        get() = System.currentTimeMillis() - lastGeneration < 3600_000L // 1 hour

    /**
     * Get formatted last generation time for display
     */
    val lastGenerationFormatted: String
        get() = if (lastGeneration == 0L) {
            "Never"
        } else {
            val instant = Instant.fromEpochMilliseconds(lastGeneration)
            instant.toString() // You might want to format this better
        }
}
