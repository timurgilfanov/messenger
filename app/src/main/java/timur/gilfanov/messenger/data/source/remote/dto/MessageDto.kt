package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.Serializable

/**
 * Network DTOs for message-related operations.
 */

@Serializable
data class MessageDto(
    val id: String,
    val chatId: String,
    val senderId: String,
    val createdAt: String, // ISO 8601 timestamp
    val editedAt: String? = null, // ISO 8601 timestamp
    val content: String,
    val deliveryStatus: DeliveryStatusDto,
)

@Serializable
sealed class DeliveryStatusDto {
    @Serializable
    data class Sending(val progress: Int) : DeliveryStatusDto()

    @Serializable
    data class Failed(val reason: String) : DeliveryStatusDto()

    @Serializable
    object Sent : DeliveryStatusDto()

    @Serializable
    object Delivered : DeliveryStatusDto()

    @Serializable
    object Read : DeliveryStatusDto()
}

// Request DTOs
@Serializable
data class SendMessageRequestDto(val chatId: String, val content: String)

@Serializable
data class EditMessageRequestDto(val content: String)

@Serializable
data class DeleteMessageRequestDto(
    val mode: String, // "FOR_SENDER" or "FOR_EVERYONE"
)
