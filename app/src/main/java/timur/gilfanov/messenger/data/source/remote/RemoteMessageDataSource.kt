package timur.gilfanov.messenger.data.source.remote

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode

/**
 * Remote data source for message-related operations.
 * Handles message operations with the remote server.
 */
interface RemoteMessageDataSource {

    /**
     * Sends a message to the remote server.
     * Returns a flow that emits message status updates (sending progress, delivered, read).
     */
    suspend fun sendMessage(message: Message): Flow<ResultWithError<Message, RemoteDataSourceError>>

    /**
     * Edits an existing message on the remote server.
     * Returns a flow that emits the updated message.
     */
    suspend fun editMessage(message: Message): Flow<ResultWithError<Message, RemoteDataSourceError>>

    /**
     * Deletes a message from the remote server.
     * @param mode Specifies whether to delete for sender only or for everyone.
     */
    suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RemoteDataSourceError>
}
