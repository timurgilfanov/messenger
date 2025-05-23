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
)
