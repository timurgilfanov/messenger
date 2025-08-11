package timur.gilfanov.messenger.data.source.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.CancellationException
import timur.gilfanov.messenger.data.source.local.database.dao.ChatDao
import timur.gilfanov.messenger.data.source.local.database.dao.MessageDao
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.toMessage
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.message.Message

/**
 * PagingSource implementation for loading messages from the Room database.
 *
 * This source loads messages in reverse chronological order (newest first) to support
 * typical chat UI patterns where users see recent messages and can scroll up for history.
 *
 * @property messageDao The DAO for accessing message data
 * @property chatDao The DAO for accessing chat and participant data
 * @property chatId The ID of the chat to load messages for
 */
class MessagePagingSource(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val chatId: ChatId,
) : PagingSource<Long, Message>() {

    override fun getRefreshKey(state: PagingState<Long, Message>): Long? =
        state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.minus(1) ?: anchorPage?.nextKey?.plus(1)
        }

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, Message> {
        return try {
            val key = params.key ?: Long.MAX_VALUE

            val messageEntities = messageDao.getMessagesByChatIdPaged(
                chatId = chatId.id.toString(),
                beforeTimestamp = key,
                limit = params.loadSize,
            )

            if (messageEntities.isEmpty()) {
                return LoadResult.Page(
                    data = emptyList(),
                    prevKey = null,
                    nextKey = null,
                )
            }

            val chatWithParticipants = chatDao.getChatWithParticipantsAndMessages(
                chatId.id.toString(),
            )
            if (chatWithParticipants == null) {
                return LoadResult.Error(IllegalStateException("Chat not found: ${chatId.id}"))
            }

            val messages = messageEntities.map { messageEntity ->
                messageEntity.toMessage(
                    participants = chatWithParticipants.participants,
                    participantCrossRefs = chatWithParticipants.participantCrossRefs,
                )
            }

            val oldestMessageTimestamp = messageEntities.lastOrNull()
                ?.createdAt
                ?.toEpochMilliseconds()
            val newestMessageTimestamp = messageEntities.firstOrNull()
                ?.createdAt
                ?.toEpochMilliseconds()

            LoadResult.Page(
                data = messages,
                prevKey = if (key == Long.MAX_VALUE) null else newestMessageTimestamp,
                nextKey = if (messageEntities.size < params.loadSize) {
                    null
                } else {
                    oldestMessageTimestamp
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 25
        const val PREFETCH_DISTANCE = 10
    }
}
