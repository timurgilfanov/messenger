package timur.gilfanov.messenger.test

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.CreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.DeleteChatError
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError
import timur.gilfanov.messenger.domain.usecase.chat.LeaveChatError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryMarkMessagesAsReadError

class ChatRepositoryStub : ChatRepository {

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, CreateChatError> =
        throw NotImplementedError()

    override suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, DeleteChatError> =
        throw NotImplementedError()

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, JoinChatError> = throw NotImplementedError()

    override suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, LeaveChatError> =
        throw NotImplementedError()

    override suspend fun flowChatList(): Flow<
        ResultWithError<List<ChatPreview>, FlowChatListError>,
        > =
        flowOf(ResultWithError.Success(emptyList()))

    override fun isChatListUpdating(): Flow<Boolean> = flowOf(false)

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = emptyFlow()

    override suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, RepositoryMarkMessagesAsReadError> = throw NotImplementedError()
}
