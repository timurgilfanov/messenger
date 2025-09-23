package timur.gilfanov.messenger.debug

import java.io.IOException

data class ClearDataError(
    val partialSuccess: Boolean,
    val failedOperations: List<Pair<String, String>>, // List of (operation, reason)
)

sealed interface GenerateDataError {
    object NoChatsGenerated : GenerateDataError
}

data class PopulateRemoteError(val reason: Exception)

sealed interface UpdateSettingsError {
    data class WriteError(val e: IOException) : UpdateSettingsError
    data class TransformError(val e: Exception) : UpdateSettingsError
}

sealed interface GetSettingsError {
    object NoData : GetSettingsError
    object ReadError : GetSettingsError
}

data class RegenerateDataError(
    val clearData: ClearDataError? = null,
    val generateData: GenerateDataError? = null,
    val populateRemote: PopulateRemoteError? = null,
    val updateSettings: UpdateSettingsError? = null,
) {
    init {
        require(
            clearData != null ||
                generateData != null ||
                populateRemote != null ||
                updateSettings != null,
        ) {
            "At least one error must be non-null"
        }
    }
}
