package timur.gilfanov.messenger.data.source.local

import java.io.IOException

sealed interface LocalUpdateSettingsError {
    data class WriteError(val e: IOException) : LocalUpdateSettingsError
    data class TransformError(val e: Exception) : LocalUpdateSettingsError
}
