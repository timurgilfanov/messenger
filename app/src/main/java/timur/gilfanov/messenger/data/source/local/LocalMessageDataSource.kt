package timur.gilfanov.messenger.data.source.local

import androidx.paging.PagingSource
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

/**
 * Local data source for message-related operations.
 * Handles storage and retrieval of message data from local database.
 */
interface LocalMessageDataSource {

    /**
     * Inserts a new message into local storage.
     */
    suspend fun insertMessage(message: Message): ResultWithError<Message, LocalDataSourceError>

    /**
     * Updates an existing message in local storage.
     */
    suspend fun updateMessage(message: Message): ResultWithError<Message, LocalDataSourceError>

    /**
     * Deletes a message from local storage.
     */
    suspend fun deleteMessage(messageId: MessageId): ResultWithError<Unit, LocalDataSourceError>

    /**
     * Retrieves a specific message from local storage.
     */
    suspend fun getMessage(messageId: MessageId): ResultWithError<Message, LocalDataSourceError>

    /**
     * Creates a PagingSource for loading messages from the local database.
     * This enables efficient pagination for large message histories.
     *
     * @param chatId The ID of the chat to load messages for
     * @return PagingSource that loads messages in pages
     */
    fun getMessagePagingSource(chatId: ChatId): PagingSource<Long, Message>
}
