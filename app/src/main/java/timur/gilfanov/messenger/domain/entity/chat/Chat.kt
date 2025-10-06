package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

@JvmInline
value class ChatId(val id: UUID)

fun String.toChatId(): ChatId = ChatId(UUID.fromString(this))

data class Chat(
    val id: ChatId,
    val name: String,
    val pictureUrl: String?,
    val messages: PersistentList<Message>,
    val participants: PersistentSet<Participant>,
    val rules: ImmutableSet<Rule>,
    val unreadMessagesCount: Int,
    val lastReadMessageId: MessageId?,
    val isClosed: Boolean = false,
    val isArchived: Boolean = false,
    val isOneToOne: Boolean = false,
)
