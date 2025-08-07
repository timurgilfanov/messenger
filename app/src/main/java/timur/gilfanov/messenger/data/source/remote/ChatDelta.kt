package timur.gilfanov.messenger.data.source.remote

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.Rule
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

sealed class ChatDelta {
    abstract val chatId: ChatId
    abstract val timestamp: Instant
}

data class ChatCreatedDelta(
    override val chatId: ChatId,
    val chatMetadata: ChatMetadata,
    val initialMessages: ImmutableList<Message>,
    override val timestamp: Instant,
) : ChatDelta()

data class ChatUpdatedDelta(
    override val chatId: ChatId,
    val chatMetadata: ChatMetadata,
    val messagesToAdd: ImmutableList<Message> = persistentListOf(),
    val messagesToDelete: ImmutableList<MessageId> = persistentListOf(),
    override val timestamp: Instant,
) : ChatDelta()

data class ChatDeletedDelta(override val chatId: ChatId, override val timestamp: Instant) :
    ChatDelta()

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

    companion object {
        fun fromChat(chat: Chat) = ChatMetadata(
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
