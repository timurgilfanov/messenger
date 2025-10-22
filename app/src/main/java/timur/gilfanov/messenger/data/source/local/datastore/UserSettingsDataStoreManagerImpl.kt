package timur.gilfanov.messenger.data.source.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import java.util.concurrent.ConcurrentHashMap
import timur.gilfanov.messenger.domain.entity.user.UserId

class UserSettingsDataStoreManagerImpl(private val context: Context) :
    UserSettingsDataStoreManager {

    private val dataStores = ConcurrentHashMap<UserId, DataStore<Preferences>>()

    override fun getDataStore(userId: UserId): DataStore<Preferences> =
        dataStores.computeIfAbsent(userId) {
            PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("user_settings_${userId.id}") },
            )
        }
}
