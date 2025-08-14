package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

fun buildChat(builder: ChatBuilder.() -> Unit): Chat = ChatBuilder().apply(builder).build()

class ChatBuilder {
    var id: ChatId = ChatId(UUID.randomUUID())
    var name: String = "Test Chat"
    var pictureUrl: String? = null
    var messages: PersistentList<Message> = persistentListOf()
    var participants: PersistentSet<Participant> = persistentSetOf()
    var rules: ImmutableSet<Rule> = persistentSetOf()
    var unreadMessagesCount: Int = 0
    var lastReadMessageId: MessageId? = null
    var isClosed: Boolean = false
    var isArchived: Boolean = false
    var isOneToOne: Boolean = false

    fun build(): Chat = Chat(
        id = id,
        name = name,
        pictureUrl = pictureUrl,
        messages = messages,
        participants = participants,
        rules = rules,
        unreadMessagesCount = unreadMessagesCount,
        lastReadMessageId = lastReadMessageId,
        isClosed = isClosed,
        isArchived = isArchived,
        isOneToOne = isOneToOne,
    )
}
