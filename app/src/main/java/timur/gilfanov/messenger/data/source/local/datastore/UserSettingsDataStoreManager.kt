package timur.gilfanov.messenger.data.source.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import timur.gilfanov.messenger.domain.entity.user.UserId

interface UserSettingsDataStoreManager {

    fun getDataStore(userId: UserId): DataStore<Preferences>
}
