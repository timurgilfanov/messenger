package timur.gilfanov.messenger.debug.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore preferences keys for debug settings.
 * These are used to persist debug configuration across app launches.
 */
object DebugPreferences {
    /**
     * Whether to use sample data on app launch
     */
    val USE_SAMPLE_DATA = booleanPreferencesKey("debug_use_sample_data")

    /**
     * The current data scenario (enum name as string)
     */
    val DATA_SCENARIO = stringPreferencesKey("debug_data_scenario")

    /**
     * Custom chat count override (if different from scenario default)
     */
    val CHAT_COUNT = intPreferencesKey("debug_chat_count")

    /**
     * Custom messages per chat override (if different from scenario default)
     */
    val MESSAGES_PER_CHAT = intPreferencesKey("debug_messages_per_chat")

    /**
     * Whether auto-activity is enabled (periodic new messages)
     */
    val AUTO_ACTIVITY_ENABLED = booleanPreferencesKey("debug_auto_activity")

    /**
     * Timestamp of last data generation
     */
    val LAST_DATA_GENERATION = longPreferencesKey("debug_last_generation")

    /**
     * Whether to show persistent debug notification
     */
    val SHOW_DEBUG_NOTIFICATION = booleanPreferencesKey("debug_show_notification")

    /**
     * Last used scenario name for quick reference
     */
    val LAST_USED_SCENARIO = stringPreferencesKey("debug_last_used_scenario")
}
