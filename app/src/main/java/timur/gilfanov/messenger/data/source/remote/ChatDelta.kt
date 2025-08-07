package timur.gilfanov.messenger.data.source.remote

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.Rule
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

sealed class ChatDelta {
    abstract val chatId: ChatId
    abstract val timestamp: Instant

    // Backward compatibility: provide chatPreview for existing code
    abstract val chatPreview: ChatPreview?
}

data class ChatCreatedDelta(
    override val chatId: ChatId,
    val chatMetadata: ChatMetadata,
    val initialMessages: ImmutableList<Message>,
    override val timestamp: Instant,
) : ChatDelta() {
    override val chatPreview: ChatPreview = chatMetadata.toChatPreview(
        chatId,
        initialMessages.lastOrNull(),
    )
}

data class ChatUpdatedDelta(
    override val chatId: ChatId,
    val chatMetadata: ChatMetadata,
    val messagesToAdd: ImmutableList<Message> = persistentListOf(),
    val messagesToDelete: ImmutableList<MessageId> = persistentListOf(),
    override val timestamp: Instant,
) : ChatDelta() {
    override val chatPreview: ChatPreview = chatMetadata.toChatPreview(
        chatId,
        messagesToAdd.lastOrNull(),
    )
}

data class ChatDeletedDelta(override val chatId: ChatId, override val timestamp: Instant) :
    ChatDelta() {
    override val chatPreview: ChatPreview? = null
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
