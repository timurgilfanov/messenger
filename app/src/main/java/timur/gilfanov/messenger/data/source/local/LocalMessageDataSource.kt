package timur.gilfanov.messenger.data.source.local

import timur.gilfanov.messenger.domain.entity.ResultWithError
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
}
