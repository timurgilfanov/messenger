package timur.gilfanov.messenger.domain.usecase.message

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.Message

/**
 * Use case for retrieving paginated messages from a specific chat.
 *
 * This use case provides efficient pagination for message histories,
 * reducing memory usage and improving performance for chats with large message counts.
 */
class GetPagedMessagesUseCase(private val messageRepository: MessageRepository) {

    /**
     * Gets paginated messages for the specified chat.
     *
     * @param chatId The ID of the chat to load messages for
     * @return Flow of PagingData containing messages in reverse chronological order
     */
    operator fun invoke(chatId: ChatId): Flow<PagingData<Message>> =
        messageRepository.getPagedMessages(chatId)
}
