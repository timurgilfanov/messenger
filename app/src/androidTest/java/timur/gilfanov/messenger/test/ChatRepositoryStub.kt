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
import timur.gilfanov.messenger.domain.usecase.chat.repository.CreateChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.DeleteChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.FlowChatListRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.JoinChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.LeaveChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.MarkMessagesAsReadRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError

class ChatRepositoryStub : ChatRepository {

    override suspend fun createChat(chat: Chat): ResultWithError<Chat, CreateChatRepositoryError> =
        throw NotImplementedError()

    override suspend fun deleteChat(
        chatId: ChatId,
    ): ResultWithError<Unit, DeleteChatRepositoryError> = throw NotImplementedError()

    override suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, JoinChatRepositoryError> = throw NotImplementedError()

    override suspend fun leaveChat(
        chatId: ChatId,
    ): ResultWithError<Unit, LeaveChatRepositoryError> = throw NotImplementedError()

    override suspend fun flowChatList(): Flow<
        ResultWithError<List<ChatPreview>, FlowChatListRepositoryError>,
        > =
        flowOf(ResultWithError.Success(emptyList()))

    override fun isChatListUpdateApplying(): Flow<Boolean> = flowOf(false)

    override suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>> = emptyFlow()

    override suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, MarkMessagesAsReadRepositoryError> = throw NotImplementedError()
}
