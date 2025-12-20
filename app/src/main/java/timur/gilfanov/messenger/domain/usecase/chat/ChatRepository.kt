package timur.gilfanov.messenger.domain.usecase.chat

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.MessageId

/**
 * Repository interface for all chat-related operations.
 *
 * This interface combines chat management, participation, and streaming operations
 * that were previously split across ParticipantRepository and PrivilegedRepository.
 * Permission checking should be handled in the use case layer.
 */
interface ChatRepository {

    // Chat Management Operations
    suspend fun createChat(chat: Chat): ResultWithError<Chat, CreateChatError>

    suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, DeleteChatError>

    // Chat Participation Operations
    suspend fun joinChat(chatId: ChatId, inviteLink: String?): ResultWithError<Chat, JoinChatError>

    suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, LeaveChatError>

    // Chat Streaming Operations
    suspend fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, FlowChatListError>>

    fun isChatListUpdating(): Flow<Boolean>

    suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>

    // Message Read Status Operations
    suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, RepositoryMarkMessagesAsReadError>
}
