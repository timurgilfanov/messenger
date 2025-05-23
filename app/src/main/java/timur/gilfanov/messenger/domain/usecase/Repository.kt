package timur.gilfanov.messenger.domain.usecase

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.message.Message

interface Repository {
    suspend fun sendMessage(message: Message): Flow<Message>
    suspend fun createChat(chat: Chat): ResultWithError<Chat, RepositoryError>
}
