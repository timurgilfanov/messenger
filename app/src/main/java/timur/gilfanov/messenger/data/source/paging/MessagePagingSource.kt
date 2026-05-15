package timur.gilfanov.messenger.data.source.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import kotlinx.coroutines.CancellationException
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.toMessage
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.Message

/**
 * Cursor used for keyset-based message pagination.
 *
 * Encodes a stable composite position as (epoch-millisecond timestamp, message id string).
 * Using both fields prevents skipping messages that share the same millisecond timestamp.
 *
 * The cursor semantics depend on the load type:
 * - [androidx.paging.PagingSource.LoadParams.Append]: exclusive — loads messages strictly older
 *   than this position.
 * - [androidx.paging.PagingSource.LoadParams.Prepend]: exclusive — loads messages strictly newer
 *   than this position.
 * - [androidx.paging.PagingSource.LoadParams.Refresh] with non-null key: inclusive — loads
 *   messages at or older than this position, so the anchor message is always in the page even
 *   when many messages share the same millisecond timestamp.
 */
data class MessageCursor(val timestamp: Long, val messageId: String)

/**
 * PagingSource implementation for loading messages from the Room database.
 *
 * This source loads messages in reverse chronological order (newest first) to support
 * typical chat UI patterns where users see recent messages and can scroll up for history.
 *
 * @property database Messenger database instance used for invalidation tracking
 * @property messageDao The DAO for accessing message data
 * @property chatDao The DAO for accessing chat and participant data
 * @property chatId The ID of the chat to load messages for
 */
class MessagePagingSource(
    private val database: MessengerDatabase,
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val chatId: ChatId,
    private val isHistoryLoaded: () -> Boolean = { false },
    private val onHistoryLoaded: () -> Unit = {},
) : PagingSource<MessageCursor, Message>() {

    private val observer = object : InvalidationTracker.Observer("messages") {
        override fun onInvalidated(tables: Set<String>) {
            invalidate()
        }
    }

    init {
        database.invalidationTracker.addObserver(observer)
        registerInvalidatedCallback {
            database.invalidationTracker.removeObserver(observer)
        }
    }

    override fun getRefreshKey(state: PagingState<MessageCursor, Message>): MessageCursor? {
        // reverseLayout = true means index 0 is the newest message (live edge).
        // Returning null when !isHistoryLoaded() or when at the live edge forces a fresh load
        // from Long.MAX_VALUE so any newly arrived messages are included.
        val anchorPosition = state.anchorPosition?.takeIf { isHistoryLoaded() && it > 0 }
        return anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.let { item ->
                MessageCursor(
                    timestamp = item.createdAt.toEpochMilliseconds(),
                    messageId = item.id.id.toString(),
                )
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(
        params: LoadParams<MessageCursor>,
    ): LoadResult<MessageCursor, Message> {
        if (params is LoadParams.Append ||
            (params is LoadParams.Refresh && params.key != null)
        ) {
            onHistoryLoaded()
        }
        return try {
            when (params) {
                is LoadParams.Prepend -> loadNewerThan(params)
                else -> loadOlderOrRefresh(params)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun loadOlderOrRefresh(
        params: LoadParams<MessageCursor>,
    ): LoadResult<MessageCursor, Message> {
        val isAnchoredRefresh = params is LoadParams.Refresh && params.key != null
        val cursor = params.key ?: MessageCursor(Long.MAX_VALUE, "")

        val messageEntities = if (isAnchoredRefresh) {
            messageDao.getMessagesByChatIdPagedFromAnchor(
                chatId = chatId.id.toString(),
                anchorTimestamp = cursor.timestamp,
                anchorId = cursor.messageId,
                limit = params.loadSize,
            )
        } else {
            messageDao.getMessagesByChatIdPaged(
                chatId = chatId.id.toString(),
                beforeTimestamp = cursor.timestamp,
                beforeMessageId = cursor.messageId,
                limit = params.loadSize,
            )
        }

        if (messageEntities.isEmpty()) {
            return LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        }

        val chatWithParticipants = chatDao.getChatWithParticipants(chatId.id.toString())
            ?: return LoadResult.Error(IllegalStateException("Chat not found: ${chatId.id}"))

        val messages = messageEntities.map { messageEntity ->
            messageEntity.toMessage(
                participants = chatWithParticipants.participants,
                participantCrossRefs = chatWithParticipants.participantCrossRefs,
            )
        }

        val oldest = messageEntities.last()
        val nextKey = if (messageEntities.size < params.loadSize) {
            null
        } else {
            MessageCursor(oldest.createdAt.toEpochMilliseconds(), oldest.id)
        }
        val prevKey = if (isAnchoredRefresh) {
            val newest = messageEntities.first()
            MessageCursor(newest.createdAt.toEpochMilliseconds(), newest.id)
        } else {
            null
        }

        return LoadResult.Page(data = messages, prevKey = prevKey, nextKey = nextKey)
    }

    private suspend fun loadNewerThan(
        params: LoadParams.Prepend<MessageCursor>,
    ): LoadResult<MessageCursor, Message> {
        val cursor = params.key
        val messageEntitiesAsc = messageDao.getMessagesByChatIdPagedNewerThan(
            chatId = chatId.id.toString(),
            afterTimestamp = cursor.timestamp,
            afterMessageId = cursor.messageId,
            limit = params.loadSize,
        )
        return if (messageEntitiesAsc.isEmpty()) {
            LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)
        } else {
            val chatWithParticipants = chatDao.getChatWithParticipants(chatId.id.toString())
                ?: return LoadResult.Error(IllegalStateException("Chat not found: ${chatId.id}"))
            // DB returns ASC (oldest-of-newer-batch first); reverse to DESC for the page list.
            val messageEntitiesDesc = messageEntitiesAsc.reversed()
            val messages = messageEntitiesDesc.map { messageEntity ->
                messageEntity.toMessage(
                    participants = chatWithParticipants.participants,
                    participantCrossRefs = chatWithParticipants.participantCrossRefs,
                )
            }
            val prevKey = if (messageEntitiesAsc.size < params.loadSize) {
                null
            } else {
                val newest = messageEntitiesDesc.first()
                MessageCursor(newest.createdAt.toEpochMilliseconds(), newest.id)
            }
            LoadResult.Page(data = messages, prevKey = prevKey, nextKey = null)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 25
        const val PREFETCH_DISTANCE = 10
    }
}
