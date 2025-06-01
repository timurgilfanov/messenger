package timur.gilfanov.messenger.data.repository

import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.Repository

class NotImplemented : Repository {
    override suspend fun leaveChat(chatId: ChatId) = error("Not implemented in delegate")
    override suspend fun createChat(chat: Chat) = error("Not implemented in delegate")
    override suspend fun deleteChat(chatId: ChatId) = error("Not implemented in delegate")
    override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
        error("Not implemented in delegate")
    override suspend fun editMessage(message: Message) = error("Not implemented in delegate")
    override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
        error("Not implemented in delegate")
    override suspend fun receiveChatUpdates(chatId: ChatId) = error("Not implemented in delegate")
    override suspend fun sendMessage(message: Message) = error("Not implemented in delegate")
}
