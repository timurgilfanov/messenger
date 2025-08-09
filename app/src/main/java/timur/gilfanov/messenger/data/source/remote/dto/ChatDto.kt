package timur.gilfanov.messenger.data.source.remote.dto

import kotlinx.serialization.Serializable

/**
 * Network DTOs for chat-related operations.
 */

@Serializable
data class ChatDto(
    val id: String,
    val name: String,
    val pictureUrl: String? = null,
    val messages: List<MessageDto> = emptyList(),
    val participants: List<ParticipantDto> = emptyList(),
    val rules: List<RuleDto> = emptyList(),
    val unreadMessagesCount: Int = 0,
    val lastReadMessageId: String? = null,
    val isClosed: Boolean = false,
    val isArchived: Boolean = false,
    val isOneToOne: Boolean = false,
)

@Serializable
data class ParticipantDto(
    val id: String,
    val name: String,
    val pictureUrl: String? = null,
    val joinedAt: String, // ISO 8601 timestamp
    val onlineAt: String? = null, // ISO 8601 timestamp
    val isAdmin: Boolean = false,
    val isModerator: Boolean = false,
)

// Reuse RuleDto from SerializationDtos.kt but add network-specific serialization
@Serializable
sealed class RuleDto {
    @Serializable
    data class CanNotWriteAfterJoining(val durationMillis: Long) : RuleDto()

    @Serializable
    data class Debounce(val delayMillis: Long) : RuleDto()

    @Serializable
    data class EditWindow(val durationMillis: Long) : RuleDto()

    @Serializable
    object SenderIdCanNotChange : RuleDto()

    @Serializable
    object RecipientCanNotChange : RuleDto()

    @Serializable
    object CreationTimeCanNotChange : RuleDto()

    @Serializable
    data class DeleteWindow(val durationMillis: Long) : RuleDto()

    @Serializable
    object SenderCanDeleteOwn : RuleDto()

    @Serializable
    object AdminCanDeleteAny : RuleDto()

    @Serializable
    object ModeratorCanDeleteAny : RuleDto()

    @Serializable
    object NoDeleteAfterDelivered : RuleDto()

    @Serializable
    data class DeleteForEveryoneWindow(val durationMillis: Long) : RuleDto()

    @Serializable
    object OnlyAdminCanDelete : RuleDto()
}

// Request DTOs
@Serializable
data class CreateChatRequestDto(
    val name: String,
    val pictureUrl: String? = null,
    val participants: List<String> = emptyList(), // participant IDs
    val rules: List<RuleDto> = emptyList(),
    val isOneToOne: Boolean = false,
)

@Serializable
data class JoinChatRequestDto(val inviteLink: String? = null)
