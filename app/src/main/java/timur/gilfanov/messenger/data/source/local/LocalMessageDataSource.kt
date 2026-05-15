package timur.gilfanov.messenger.data.source.local

import androidx.paging.PagingSource
import timur.gilfanov.messenger.data.source.paging.MessageCursor
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
     * @param isHistoryLoaded shared state that persists across PagingSource invalidations;
     *   returns true once the user has scrolled past the live edge (an Append or anchored Refresh
     *   has run), so that [getRefreshKey] can anchor subsequent refreshes to preserve scroll
     *   position instead of always reloading from the live edge
     * @param onHistoryLoaded callback invoked when an Append or anchored Refresh occurs, to set
     *   the shared [isHistoryLoaded] flag
     * @return PagingSource that loads messages in pages
     */
    fun getMessagePagingSource(
        chatId: ChatId,
        isHistoryLoaded: () -> Boolean = { false },
        onHistoryLoaded: () -> Unit = {},
    ): PagingSource<MessageCursor, Message>
}
