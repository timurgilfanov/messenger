package timur.gilfanov.messenger.domain.usecase.chat

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.repository.CreateChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.DeleteChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.FlowChatListRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.JoinChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.LeaveChatRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.MarkMessagesAsReadRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError

/**
 * Repository interface for all chat-related operations.
 *
 * This interface combines chat management, participation, and streaming operations
 * that were previously split across ParticipantRepository and PrivilegedRepository.
 * Permission checking should be handled in the use case layer.
 */
interface ChatRepository {

    // Chat Management Operations
    suspend fun createChat(chat: Chat): ResultWithError<Chat, CreateChatRepositoryError>

    suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, DeleteChatRepositoryError>

    // Chat Participation Operations
    suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, JoinChatRepositoryError>

    suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, LeaveChatRepositoryError>

    // Chat Streaming Operations
    suspend fun flowChatList(): Flow<
        ResultWithError<List<ChatPreview>, FlowChatListRepositoryError>,
        >

    fun isChatListUpdateApplying(): Flow<Boolean>

    suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>>

    // Message Read Status Operations
    suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, MarkMessagesAsReadRepositoryError>
}
