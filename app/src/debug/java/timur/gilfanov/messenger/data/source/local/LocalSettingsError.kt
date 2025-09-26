package timur.gilfanov.messenger.data.source.local

import java.io.IOException

sealed interface LocalGetSettingsError : LocalDataSourceError {
    data class ReadError(val exception: IOException) : LocalGetSettingsError
}

sealed interface LocalClearSyncTimestampError : LocalDataSourceError {
    data class WriteError(val exception: IOException) : LocalClearSyncTimestampError
}

sealed interface LocalUpdateSettingsError : LocalDataSourceError {
    data class WriteError(val exception: IOException) : LocalUpdateSettingsError
    data class TransformError(val exception: Exception) : LocalUpdateSettingsError
}
