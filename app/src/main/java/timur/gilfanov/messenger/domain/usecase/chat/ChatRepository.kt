package timur.gilfanov.messenger.domain.usecase.chat

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview

/**
 * Repository interface for all chat-related operations.
 *
 * This interface combines chat management, participation, and streaming operations
 * that were previously split across ParticipantRepository and PrivilegedRepository.
 * Permission checking should be handled in the use case layer.
 */
interface ChatRepository {

    // Chat Management Operations
    suspend fun createChat(chat: Chat): ResultWithError<Chat, RepositoryCreateChatError>

    suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, RepositoryDeleteChatError>

    // Chat Participation Operations
    suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RepositoryJoinChatError>

    suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, RepositoryLeaveChatError>

    // Chat Streaming Operations
    suspend fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, FlowChatListError>>

    fun isChatListUpdating(): Flow<Boolean>

    suspend fun receiveChatUpdates(
        chatId: ChatId,
    ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>
}
