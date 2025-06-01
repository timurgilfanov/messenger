package timur.gilfanov.messenger.domain.usecase.privileged

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId

interface PrivilegedRepository {
    suspend fun createChat(chat: Chat): ResultWithError<Chat, RepositoryCreateChatError>
    suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, RepositoryDeleteChatError>
}
