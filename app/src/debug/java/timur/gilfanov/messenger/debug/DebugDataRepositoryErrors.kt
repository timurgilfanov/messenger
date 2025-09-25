package timur.gilfanov.messenger.debug

import java.io.IOException
import timur.gilfanov.messenger.data.source.remote.AddChatError
import timur.gilfanov.messenger.domain.entity.chat.ChatId

data class ClearDataError(
    val partialSuccess: Boolean,
    val failedOperations: List<Pair<String, String>>, // List of (operation, reason)
)

sealed interface GenerateDataError {
    object NoChatsGenerated : GenerateDataError
}

data class PopulateRemoteError(val addChatError: Map<ChatId, AddChatError>)

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
    val getSettings: GetSettingsError? = null,
    val updateSettings: UpdateSettingsError? = null,
) {
    init {
        require(
            clearData != null ||
                generateData != null ||
                populateRemote != null ||
                getSettings != null ||
                updateSettings != null,
        ) {
            "At least one error must be non-null"
        }
    }
}
