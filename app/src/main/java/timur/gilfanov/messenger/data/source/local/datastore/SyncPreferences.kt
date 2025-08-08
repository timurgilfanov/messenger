package timur.gilfanov.messenger.data.source.local.datastore

import androidx.datastore.preferences.core.longPreferencesKey

/**
 * DataStore preferences keys for synchronization data.
 */
object SyncPreferences {
    /**
     * Key for storing the last sync timestamp as epoch milliseconds.
     * Null or missing value means no sync has been performed yet.
     */
    val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
}
