package timur.gilfanov.messenger.data.source.remote

import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId

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
}
