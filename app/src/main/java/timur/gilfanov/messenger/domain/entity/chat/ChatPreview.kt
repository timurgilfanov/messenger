package timur.gilfanov.messenger.domain.entity.chat

import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableSet
import timur.gilfanov.messenger.domain.entity.message.Message

data class ChatPreview(
    val id: ChatId,
    val participants: ImmutableSet<Participant>,
    val name: String,
    val pictureUrl: String?,
    val rules: ImmutableSet<Rule>,
    val unreadMessagesCount: Int,
    val lastReadMessageId: timur.gilfanov.messenger.domain.entity.message.MessageId?,
    val lastMessage: Message?,
    val lastActivityAt: Instant?,
) {
    override fun toString(): String = buildString {
        appendLine("ChatPreview(")
        appendLine("  id=$id, name='$name', pictureUrl=$pictureUrl")
        appendLine("  participants=${participants.size}")
        appendLine("  rules=${rules.joinToString()}")
        appendLine("  unreadMessagesCount=$unreadMessagesCount")
        appendLine("  lastReadMessageId=$lastReadMessageId")
        appendLine("  lastMessage=$lastMessage")
        appendLine("  lastActivityAt=$lastActivityAt")
        appendLine(")")
    }

    companion object {
        fun fromChat(chat: Chat): ChatPreview = ChatPreview(
            id = chat.id,
            participants = chat.participants,
            name = chat.name,
            pictureUrl = chat.pictureUrl,
            rules = chat.rules,
            unreadMessagesCount = chat.unreadMessagesCount,
            lastReadMessageId = chat.lastReadMessageId,
            lastMessage = chat.messages.lastOrNull(),
            lastActivityAt = chat.messages.lastOrNull()?.createdAt,
        )
    }
}
