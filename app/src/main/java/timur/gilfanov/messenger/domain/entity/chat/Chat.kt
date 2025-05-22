package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import timur.gilfanov.messenger.domain.entity.message.Message

data class Chat(
    val id: UUID, // TODO use inline class
    val name: String,
    val pictureUrl: String?,
    val messages: List<Message>, // TODO Use truly immutable collection
    val participants: Set<Participant>, // TODO Use truly immutable collection
    val rules: Set<Rule>, // TODO Use truly immutable collection
    val unreadMessagesCount: Int,
    val lastReadMessageId: UUID?, // TODO use inline class
)
