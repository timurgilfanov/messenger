package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

@JvmInline
value class ChatId(val id: UUID)

data class Chat(
    val id: ChatId,
    val name: String,
    val pictureUrl: String?,
    val messages: ImmutableList<Message>,
    val participants: ImmutableSet<Participant>,
    val rules: ImmutableSet<Rule>,
    val unreadMessagesCount: Int,
    val lastReadMessageId: MessageId?,
    val isClosed: Boolean = false,
    val isArchived: Boolean = false,
    val isOneToOne: Boolean = false,
) {
    override fun toString(): String = buildString {
        appendLine("Chat(")
        appendLine("  id=$id")
        appendLine("  name='$name'")
        appendLine("  pictureUrl=${pictureUrl?.let { "'$it'" } ?: "null"}")
        appendLine("  messages=${messages.size} items (${messages.joinToString()})")
        appendLine("  participants=${participants.size} items")
        appendLine("  rules=${rules.size} items")
        appendLine("  unreadMessagesCount=$unreadMessagesCount")
        appendLine("  lastReadMessageId=$lastReadMessageId")
        appendLine("  isClosed=$isClosed")
        appendLine("  isArchived=$isArchived")
        appendLine("  isOneToOne=$isOneToOne")
        append(")")
    }
}
