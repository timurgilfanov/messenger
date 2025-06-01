package timur.gilfanov.messenger.domain.usecase

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.participant.message.RepositoryDeleteMessageError
import timur.gilfanov.messenger.domain.usecase.priveleged.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.priveleged.RepositoryDeleteChatError

interface Repository {
    suspend fun sendMessage(message: Message): Flow<Message>
    suspend fun editMessage(message: Message): Flow<Message>
    suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RepositoryDeleteMessageError>
    suspend fun createChat(chat: Chat): ResultWithError<Chat, RepositoryCreateChatError>
    suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>
    suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, RepositoryDeleteChatError>
    suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RepositoryJoinChatError>
    suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, RepositoryLeaveChatError>
}
