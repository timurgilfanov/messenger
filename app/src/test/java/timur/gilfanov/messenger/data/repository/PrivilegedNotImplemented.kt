package timur.gilfanov.messenger.data.repository

import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.priveleged.PrivilegedRepository

class PrivilegedNotImplemented : PrivilegedRepository {
    override suspend fun createChat(chat: Chat) = error("Not implemented in delegate")
    override suspend fun deleteChat(chatId: ChatId) = error("Not implemented in delegate")
}
