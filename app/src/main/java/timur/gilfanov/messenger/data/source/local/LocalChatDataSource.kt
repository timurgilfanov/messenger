package timur.gilfanov.messenger.data.source.local

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview

/**
 * Local data source for chat-related operations.
 * Handles storage and retrieval of chat data from local database.
 */
interface LocalChatDataSource {

    /**
     * Inserts a new chat into local storage.
     */
    suspend fun insertChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError>

    /**
     * Updates an existing chat in local storage.
     */
    suspend fun updateChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError>

    /**
     * Deletes a chat from local storage.
     */
    suspend fun deleteChat(chatId: ChatId): ResultWithError<Unit, LocalDataSourceError>

    /**
     * Returns a flow of chat list from local storage.
     * Emits updates when the chat list changes.
     */
    fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, LocalDataSourceError>>

    /**
     * Returns a flow of updates for a specific chat.
     * Emits when the chat data changes.
     */
    fun flowChatUpdates(chatId: ChatId): Flow<ResultWithError<Chat, LocalDataSourceError>>
}
