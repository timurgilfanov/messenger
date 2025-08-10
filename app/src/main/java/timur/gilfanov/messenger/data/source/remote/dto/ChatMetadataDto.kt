package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChatMetadataDto(
    val name: String,
    val participants: List<ParticipantDto>,
    val pictureUrl: String? = null,
    val rules: List<RuleDto>,
    val unreadMessagesCount: Int,
    val lastReadMessageId: String? = null, // MessageId as string
    val lastActivityAt: String? = null, // ISO 8601 timestamp
)
