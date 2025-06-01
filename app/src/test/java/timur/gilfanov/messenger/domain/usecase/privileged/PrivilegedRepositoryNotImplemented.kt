package timur.gilfanov.messenger.domain.usecase.privileged

import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId

class PrivilegedRepositoryNotImplemented : PrivilegedRepository {
    override suspend fun createChat(chat: Chat) = error("Not implemented in delegate")
    override suspend fun deleteChat(chatId: ChatId) = error("Not implemented in delegate")
}
