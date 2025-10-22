package timur.gilfanov.messenger.data.source.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import java.util.concurrent.ConcurrentHashMap
import timur.gilfanov.messenger.data.source.local.datastore.UserSettingsDataStoreManager
import timur.gilfanov.messenger.data.source.local.datastore.UserSettingsDataStoreManagerImpl
import timur.gilfanov.messenger.domain.entity.user.UserId

class UserSettingsDataStoreManagerFake(private val context: Context) :
    UserSettingsDataStoreManager {

    val realManager = UserSettingsDataStoreManagerImpl(context)

    private val dataStores = ConcurrentHashMap<UserId, DataStoreFake>()

    fun clear(userId: UserId) {
        context.preferencesDataStoreFile("user_settings_${userId.id}").delete()
    }

    fun setReadError(userId: UserId, enabled: Boolean = true) {
        getOrPut(userId).readError = enabled
    }

    fun setWriteError(userId: UserId, enabled: Boolean = true) {
        getOrPut(userId).writeError = enabled
    }

    private fun getOrPut(userId: UserId): DataStoreFake = dataStores.getOrPut(userId) {
        DataStoreFake(dataStore = realManager.getDataStore(userId))
    }

    override fun getDataStore(userId: UserId): DataStore<Preferences> = getOrPut(userId)
}
