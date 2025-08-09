package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.Serializable

/**
 * Network DTOs for message-related operations.
 */

@Serializable
data class MessageDto(
    val id: String,
    val parentId: String? = null,
    val sender: ParticipantDto,
    val recipient: String, // ChatId as string
    val createdAt: String, // ISO 8601 timestamp
    val sentAt: String? = null, // ISO 8601 timestamp
    val deliveredAt: String? = null, // ISO 8601 timestamp
    val editedAt: String? = null, // ISO 8601 timestamp
    val deliveryStatus: DeliveryStatusDto? = null,
    val content: String,
    val type: String = "text", // Message type discriminator
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
data class SendMessageRequestDto(
    val recipient: String, // ChatId as string
    val content: String,
    val parentId: String? = null,
)

@Serializable
data class EditMessageRequestDto(val content: String)

@Serializable
data class DeleteMessageRequestDto(
    val mode: String, // "FOR_SENDER_ONLY" or "FOR_EVERYONE"
)
