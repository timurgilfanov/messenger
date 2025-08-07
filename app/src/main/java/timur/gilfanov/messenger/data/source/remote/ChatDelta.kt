package timur.gilfanov.messenger.data.source.remote

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.Rule
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

/**
 * Represents a single incremental change to a chat.
 * Uses hybrid approach: full metadata + incremental messages for efficiency.
 */
data class ChatDelta(
    val chatId: ChatId,
    val operation: DeltaOperation,
    val chatMetadata: ChatMetadata?, // null for DELETE operations
    val incrementalMessages: ImmutableList<Message>, // Only messages since last sync
    val messagesToDelete: ImmutableList<MessageId>,
    val timestamp: Instant,
) {
    init {
        when (operation) {
            DeltaOperation.CREATE -> {
                require(chatMetadata != null) {
                    "ChatMetadata required for CREATE/UPDATE operations"
                }
                require(messagesToDelete.isEmpty()) {
                    "MessagesToDelete must be empty for CREATE operations"
                }
            }
            DeltaOperation.UPDATE -> {
                require(chatMetadata != null) {
                    "ChatMetadata required for CREATE/UPDATE operations"
                }
            }
            DeltaOperation.DELETE -> {
                require(chatMetadata == null) { "ChatMetadata must be null for DELETE operations" }
                require(messagesToDelete.isEmpty()) {
                    "MessagesToDelete must be empty for CREATE operations"
                }
            }
        }
    }

    // Backward compatibility: provide chatPreview for existing code
    val chatPreview: ChatPreview?
        get() = chatMetadata?.toChatPreview(chatId, incrementalMessages.lastOrNull())
}

/**
 * Contains full chat metadata (lightweight data that's always synced completely).
 * Messages are handled separately as incremental updates for bandwidth efficiency.
 */
data class ChatMetadata(
    val name: String,
    val participants: ImmutableSet<Participant>,
    val pictureUrl: String?,
    val rules: ImmutableSet<Rule>,
    val unreadMessagesCount: Int,
    val lastReadMessageId: MessageId?,
    val lastActivityAt: Instant?,
) {
    fun toChatPreview(chatId: ChatId, lastMessage: Message?): ChatPreview = ChatPreview(
        id = chatId,
        participants = participants,
        name = name,
        pictureUrl = pictureUrl,
        rules = rules,
        unreadMessagesCount = unreadMessagesCount,
        lastReadMessageId = lastReadMessageId,
        lastMessage = lastMessage,
        lastActivityAt = lastActivityAt,
    )

    companion object {
        fun fromChat(chat: timur.gilfanov.messenger.domain.entity.chat.Chat): ChatMetadata =
            ChatMetadata(
                name = chat.name,
                participants = chat.participants,
                pictureUrl = chat.pictureUrl,
                rules = chat.rules,
                unreadMessagesCount = chat.unreadMessagesCount,
                lastReadMessageId = chat.lastReadMessageId,
                lastActivityAt = chat.messages.lastOrNull()?.createdAt,
            )
    }
}
