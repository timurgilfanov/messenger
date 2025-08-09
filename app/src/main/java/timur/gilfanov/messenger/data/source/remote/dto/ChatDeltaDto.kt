package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.Serializable

@Serializable
sealed class ChatDeltaDto {
    abstract val chatId: String
    abstract val timestamp: String // ISO 8601 timestamp
}

@Serializable
data class ChatCreatedDeltaDto(
    override val chatId: String,
    val chatMetadata: ChatMetadataDto,
    val initialMessages: List<MessageDto>,
    override val timestamp: String,
) : ChatDeltaDto()

@Serializable
data class ChatUpdatedDeltaDto(
    override val chatId: String,
    val chatMetadata: ChatMetadataDto,
    val messagesToAdd: List<MessageDto> = emptyList(),
    val messagesToDelete: List<String> = emptyList(), // MessageIds as strings
    override val timestamp: String,
) : ChatDeltaDto()

@Serializable
data class ChatDeletedDeltaDto(override val chatId: String, override val timestamp: String) :
    ChatDeltaDto()
