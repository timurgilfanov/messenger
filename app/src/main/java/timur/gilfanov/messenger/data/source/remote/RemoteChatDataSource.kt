package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.MessageId

/**
 * Remote data source for chat-related operations.
 * Handles chat operations with the remote server.
 */
interface RemoteChatDataSource {

    /**
     * Creates a new chat on the remote server.
     */
    suspend fun createChat(chat: Chat): ResultWithError<Chat, RemoteDataSourceError>

    /**
     * Deletes a chat from the remote server.
     */
    suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError>

    /**
     * Joins an existing chat on the remote server.
     * @param inviteLink Optional invite link for private chats.
     */
    suspend fun joinChat(
        chatId: ChatId,
        inviteLink: String?,
    ): ResultWithError<Chat, RemoteDataSourceError>

    /**
     * Leaves a chat on the remote server.
     */
    suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, RemoteDataSourceError>

    /**
     * Marks messages in a chat as read up to and including the specified message.
     * This prevents race conditions where undelivered messages could be marked as read.
     * @param chatId The chat where messages should be marked as read
     * @param upToMessageId Mark messages as read up to and including this message ID
     */
    suspend fun markMessagesAsRead(
        chatId: ChatId,
        upToMessageId: MessageId,
    ): ResultWithError<Unit, RemoteDataSourceError>
}
