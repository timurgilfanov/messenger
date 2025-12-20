package timur.gilfanov.messenger.domain.usecase.message

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
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

    suspend fun sendMessage(message: Message): Flow<ResultWithError<Message, SendMessageError>>

    suspend fun editMessage(message: Message): Flow<ResultWithError<Message, EditMessageError>>

    suspend fun deleteMessage(
        messageId: MessageId,
        mode: DeleteMessageMode,
    ): ResultWithError<Unit, DeleteMessageError>

    /**
     * Returns a Flow of PagingData for messages in a specific chat.
     * This enables efficient pagination for large message histories.
     *
     * @param chatId The ID of the chat to load messages for
     * @return Flow of PagingData containing messages
     */
    fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>>
}
