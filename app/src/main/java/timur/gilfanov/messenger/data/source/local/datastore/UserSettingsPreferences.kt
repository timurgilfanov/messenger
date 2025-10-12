package timur.gilfanov.messenger.data.source.local.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object UserSettingsPreferences {
    val METADATA_DEFAULT = booleanPreferencesKey("metadata_default")
    val METADATA_LAST_MODIFIED_AT = longPreferencesKey("metadata_last_modified_at")
    val METADATA_LAST_SYNCED_AT = longPreferencesKey("metadata_last_synced_at")

    val UI_LANGUAGE = stringPreferencesKey("ui_language")
}
