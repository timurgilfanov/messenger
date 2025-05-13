package timur.gilfanov.messenger.entity.model.chat

import java.util.UUID
import timur.gilfanov.messenger.entity.model.message.Message

data class Chat(val id: UUID, val messages: MutableList<Message>, val users: MutableList<ChatUser>)

val Chat.isGroupChat: Boolean
    get() = users.size > 2
