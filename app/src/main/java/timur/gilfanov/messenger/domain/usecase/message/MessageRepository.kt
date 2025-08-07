package timur.gilfanov.messenger.domain.usecase.message

import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

/**
 * Repository interface for all message-related operations.
 *
 * This interface handles message lifecycle operations (send, edit, delete)
 * that were previously part of ParticipantRepository.
 * Permission checking should be handled in the use case layer.
 */
interface MessageRepository {

    suspend fun sendMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RepositorySendMessageError>>

    suspend fun editMessage(
        message: Message,
    ): Flow<ResultWithError<Message, RepositoryEditMessageError>>

    suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, RepositoryDeleteMessageError>
}
