package timur.gilfanov.messenger.data.source.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreFake(
    private val dataStore: DataStore<Preferences>,
    var readError: Boolean = false,
    var writeError: Boolean = false,
    var transformError: Boolean = false,
) : DataStore<Preferences> {

    override val data: Flow<Preferences>
        get() = dataStore.data.map {
            if (readError) {
                throw androidx.datastore.core.IOException(
                    "Read data fake exception",
                )
            } else {
                it
            }
        }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        if (writeError) throw androidx.datastore.core.IOException("Write data fake exception")
        return dataStore.updateData {
            @Suppress("TooGenericExceptionThrown")
            if (transformError) throw Exception("Transform data fake exception")
            transform(it)
        }
    }
}
