package timur.gilfanov.messenger.data.source.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.entity.ChatEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class MessagePagingSourceTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    @Before
    fun setup() = runTest {
        setupTestChatAndParticipant()
    }

    @Test
    fun `refresh loads newest page first`() = runTest {
        insertThreeMessages()

        val result = pagingSource().load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            ),
        )

        val page = assertIs<PagingSource.LoadResult.Page<MessageCursor, Message>>(result)
        assertEquals(listOf(NEWEST_TEXT, MIDDLE_TEXT), page.data.texts())
        assertNull(page.prevKey)
        assertEquals(
            MessageCursor(MIDDLE_TIMESTAMP, pagedMessageId(MIDDLE_SUFFIX)),
            page.nextKey,
        )
    }

    @Test
    fun `anchored refresh includes anchor with same-millisecond collision`() = runTest {
        // Regression guard: an anchored refresh must keep the anchor in the page even when more
        // than loadSize messages share its exact millisecond timestamp. The keyset query is
        // inclusive on the anchor id (createdAt = anchorTs AND id <= anchorId), so the
        // lexicographically lowest-id anchor is not pushed off the first page by higher-id siblings.
        val sameTimestamp = Instant.fromEpochMilliseconds(SAME_TIMESTAMP)
        val highIds = listOf(HIGH_ID_2, HIGH_ID_3, HIGH_ID_4)
        (listOf(ANCHOR_ID) + highIds).forEach { id ->
            insertMessage(
                createTestMessage(
                    id = id,
                    text = id,
                    createdAt = sameTimestamp,
                    sentAt = sameTimestamp,
                ),
            )
        }
        // Two older messages to fill the page alongside the anchor.
        insertMessage(
            createTestMessage(
                id = OLDER_ID_1,
                text = OLDER_1_TEXT,
                createdAt = Instant.fromEpochMilliseconds(OLDER_TIMESTAMP),
                sentAt = Instant.fromEpochMilliseconds(OLDER_TIMESTAMP),
            ),
        )
        insertMessage(
            createTestMessage(
                id = OLDER_ID_2,
                text = OLDER_2_TEXT,
                createdAt = Instant.fromEpochMilliseconds(OLDER_TIMESTAMP),
                sentAt = Instant.fromEpochMilliseconds(OLDER_TIMESTAMP),
            ),
        )

        val result = pagingSource().load(
            PagingSource.LoadParams.Refresh(
                key = MessageCursor(SAME_TIMESTAMP, ANCHOR_ID),
                loadSize = ANCHOR_LOAD_SIZE,
                placeholdersEnabled = false,
            ),
        )

        val page = assertIs<PagingSource.LoadResult.Page<MessageCursor, Message>>(result)
        val ids = page.data.map { it.id.id.toString() }
        assertTrue(ids.contains(ANCHOR_ID), "Anchor message must be in the refreshed page")
        assertNotNull(page.prevKey)
    }

    @Test
    fun `append loads older page`() = runTest {
        insertThreeMessages()

        val result = pagingSource().load(
            PagingSource.LoadParams.Append(
                key = MessageCursor(MIDDLE_TIMESTAMP, pagedMessageId(MIDDLE_SUFFIX)),
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            ),
        )

        val page = assertIs<PagingSource.LoadResult.Page<MessageCursor, Message>>(result)
        assertEquals(listOf(OLDEST_TEXT), page.data.texts())
        assertNull(page.prevKey)
        assertNull(page.nextKey)
    }

    @Test
    fun `prepend loads newer page in reverse order`() = runTest {
        insertThreeMessages()

        val result = pagingSource().load(
            PagingSource.LoadParams.Prepend(
                key = MessageCursor(OLDEST_TIMESTAMP, pagedMessageId(OLDEST_SUFFIX)),
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            ),
        )

        val page = assertIs<PagingSource.LoadResult.Page<MessageCursor, Message>>(result)
        // Newer-than query returns ASC and is reversed to DESC for the page list.
        assertEquals(listOf(NEWEST_TEXT, MIDDLE_TEXT), page.data.texts())
        assertEquals(
            MessageCursor(NEWEST_TIMESTAMP, pagedMessageId(NEWEST_SUFFIX)),
            page.prevKey,
        )
        assertNull(page.nextKey)
    }

    @Test
    fun `prepend returns null prevKey when fewer than page size`() = runTest {
        insertThreeMessages()

        val result = pagingSource().load(
            PagingSource.LoadParams.Prepend(
                key = MessageCursor(MIDDLE_TIMESTAMP, pagedMessageId(MIDDLE_SUFFIX)),
                loadSize = OVERSIZED_PAGE,
                placeholdersEnabled = false,
            ),
        )

        val page = assertIs<PagingSource.LoadResult.Page<MessageCursor, Message>>(result)
        assertEquals(listOf(NEWEST_TEXT), page.data.texts())
        assertNull(page.prevKey)
        assertNull(page.nextKey)
    }

    @Test
    fun `prepend at live edge returns empty page`() = runTest {
        insertThreeMessages()

        val result = pagingSource().load(
            PagingSource.LoadParams.Prepend(
                key = MessageCursor(NEWEST_TIMESTAMP, pagedMessageId(NEWEST_SUFFIX)),
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            ),
        )

        val page = assertIs<PagingSource.LoadResult.Page<MessageCursor, Message>>(result)
        assertTrue(page.data.isEmpty())
        assertNull(page.prevKey)
        assertNull(page.nextKey)
    }

    @Test
    fun `onHistoryLoaded invoked only for append and anchored refresh`() = runTest {
        insertThreeMessages()
        var historyLoadedCount = 0
        fun source() = pagingSource(onHistoryLoaded = { historyLoadedCount++ })
        val middleCursor = MessageCursor(MIDDLE_TIMESTAMP, pagedMessageId(MIDDLE_SUFFIX))
        val newestCursor = MessageCursor(NEWEST_TIMESTAMP, pagedMessageId(NEWEST_SUFFIX))

        // Refresh from the live edge (null key) is not a history load.
        source().load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            ),
        )
        assertEquals(NO_HISTORY_LOADS, historyLoadedCount)

        // Prepend (newer-than) is not a history load.
        source().load(
            PagingSource.LoadParams.Prepend(
                key = middleCursor,
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            ),
        )
        assertEquals(NO_HISTORY_LOADS, historyLoadedCount)

        // Append (older) is a history load.
        source().load(
            PagingSource.LoadParams.Append(
                key = middleCursor,
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            ),
        )
        assertEquals(ONE_HISTORY_LOAD, historyLoadedCount)

        // Anchored refresh (non-null key) is a history load.
        source().load(
            PagingSource.LoadParams.Refresh(
                key = newestCursor,
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            ),
        )
        assertEquals(TWO_HISTORY_LOADS, historyLoadedCount)
    }

    @Test
    fun `getRefreshKey returns null when history not loaded`() {
        val source = pagingSource(isHistoryLoaded = { false })

        assertNull(source.getRefreshKey(refreshKeyState(anchorPosition = ANCHOR_POSITION)))
    }

    @Test
    fun `getRefreshKey returns null at live edge`() {
        val source = pagingSource(isHistoryLoaded = { true })

        assertNull(source.getRefreshKey(refreshKeyState(anchorPosition = LIVE_EDGE_POSITION)))
    }

    @Test
    fun `getRefreshKey returns null when anchor position is null`() {
        val source = pagingSource(isHistoryLoaded = { true })

        assertNull(source.getRefreshKey(refreshKeyState(anchorPosition = null)))
    }

    @Test
    fun `getRefreshKey returns anchored cursor when history loaded`() {
        val source = pagingSource(isHistoryLoaded = { true })

        val key = source.getRefreshKey(refreshKeyState(anchorPosition = ANCHOR_POSITION))

        assertEquals(
            MessageCursor(MIDDLE_TIMESTAMP, pagedMessageId(MIDDLE_SUFFIX)),
            key,
        )
    }

    private fun pagingSource(
        isHistoryLoaded: () -> Boolean = { false },
        onHistoryLoaded: () -> Unit = {},
    ) = MessagePagingSource(
        database = databaseRule.database,
        messageDao = databaseRule.messageDao,
        chatDao = databaseRule.chatDao,
        chatId = defaultChatId,
        isHistoryLoaded = isHistoryLoaded,
        onHistoryLoaded = onHistoryLoaded,
    )

    private suspend fun insertMessage(message: Message) {
        databaseRule.messageDao.insertMessage(with(EntityMappers) { message.toMessageEntity() })
    }

    private fun pagedMessageId(suffix: String) = "00000000-0000-0000-0000-0000000003$suffix"

    private suspend fun insertThreeMessages() {
        insertMessage(
            createTestMessage(
                id = pagedMessageId(OLDEST_SUFFIX),
                text = OLDEST_TEXT,
                createdAt = Instant.fromEpochMilliseconds(OLDEST_TIMESTAMP),
                sentAt = Instant.fromEpochMilliseconds(OLDEST_TIMESTAMP),
            ),
        )
        insertMessage(
            createTestMessage(
                id = pagedMessageId(MIDDLE_SUFFIX),
                text = MIDDLE_TEXT,
                createdAt = Instant.fromEpochMilliseconds(MIDDLE_TIMESTAMP),
                sentAt = Instant.fromEpochMilliseconds(MIDDLE_TIMESTAMP),
            ),
        )
        insertMessage(
            createTestMessage(
                id = pagedMessageId(NEWEST_SUFFIX),
                text = NEWEST_TEXT,
                createdAt = Instant.fromEpochMilliseconds(NEWEST_TIMESTAMP),
                sentAt = Instant.fromEpochMilliseconds(NEWEST_TIMESTAMP),
            ),
        )
    }

    private fun refreshKeyState(anchorPosition: Int?): PagingState<MessageCursor, Message> {
        fun message(suffix: String, text: String, timestamp: Long) = createTestMessage(
            id = pagedMessageId(suffix),
            text = text,
            createdAt = Instant.fromEpochMilliseconds(timestamp),
            sentAt = Instant.fromEpochMilliseconds(timestamp),
        )
        // reverseLayout = true: index 0 is the newest message.
        val page = PagingSource.LoadResult.Page<MessageCursor, Message>(
            data = listOf(
                message(NEWEST_SUFFIX, NEWEST_TEXT, NEWEST_TIMESTAMP),
                message(MIDDLE_SUFFIX, MIDDLE_TEXT, MIDDLE_TIMESTAMP),
                message(OLDEST_SUFFIX, OLDEST_TEXT, OLDEST_TIMESTAMP),
            ),
            prevKey = null,
            nextKey = null,
        )
        return PagingState(
            pages = listOf(page),
            anchorPosition = anchorPosition,
            config = PagingConfig(pageSize = PAGE_SIZE),
            leadingPlaceholderCount = 0,
        )
    }

    private suspend fun setupTestChatAndParticipant() {
        databaseRule.chatDao.insertChat(
            ChatEntity(
                id = CHAT_ID,
                name = "Test Chat",
                pictureUrl = null,
                rules = "[]",
                unreadMessagesCount = 0,
                lastReadMessageId = null,
                updatedAt = Instant.fromEpochMilliseconds(CHAT_UPDATED_AT),
            ),
        )
        databaseRule.participantDao.insertParticipant(
            ParticipantEntity(
                id = PARTICIPANT_ID,
                name = "Test Sender",
                pictureUrl = null,
                onlineAt = null,
            ),
        )
        databaseRule.chatDao.insertChatParticipantCrossRef(
            ChatParticipantCrossRef(
                chatId = CHAT_ID,
                participantId = PARTICIPANT_ID,
                joinedAt = Instant.fromEpochMilliseconds(JOINED_AT),
                isAdmin = false,
                isModerator = false,
                isCurrentUser = true,
            ),
        )
    }

    private val defaultChatId = ChatId(UUID.fromString(CHAT_ID))

    private fun List<Message>.texts(): List<String> = map { message ->
        assertIs<TextMessage>(message).text
    }

    @Suppress("LongParameterList") // Test helper needs flexibility
    private fun createTestMessage(
        id: String,
        text: String,
        createdAt: Instant,
        sentAt: Instant,
    ) = TextMessage(
        id = MessageId(UUID.fromString(id)),
        recipient = defaultChatId,
        sender = Participant(
            id = ParticipantId(UUID.fromString(PARTICIPANT_ID)),
            name = "Test Sender",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(JOINED_AT),
            onlineAt = null,
            isAdmin = false,
            isModerator = false,
        ),
        parentId = null,
        text = text,
        deliveryStatus = DeliveryStatus.Sent,
        createdAt = createdAt,
        sentAt = sentAt,
        deliveredAt = null,
        editedAt = null,
    )

    companion object {
        private const val CHAT_ID = "11111111-1111-1111-1111-111111111111"
        private const val PARTICIPANT_ID = "22222222-2222-2222-2222-222222222222"

        private const val JOINED_AT = 900000L
        private const val CHAT_UPDATED_AT = 1000000L

        // Paging fixture (oldest / middle / newest), shared by insertThreeMessages and refreshKeyState
        private const val OLDEST_TIMESTAMP = 1000L
        private const val MIDDLE_TIMESTAMP = 2000L
        private const val NEWEST_TIMESTAMP = 3000L
        private const val OLDEST_TEXT = "Oldest"
        private const val MIDDLE_TEXT = "Middle"
        private const val NEWEST_TEXT = "Newest"
        private const val OLDEST_SUFFIX = "01"
        private const val MIDDLE_SUFFIX = "02"
        private const val NEWEST_SUFFIX = "03"

        // Anchored-refresh same-millisecond collision fixture
        private const val SAME_TIMESTAMP = 7000L
        private const val OLDER_TIMESTAMP = 6000L
        private const val ANCHOR_LOAD_SIZE = 3
        private const val ANCHOR_ID = "07000001-0000-0000-0000-000000000001"
        private const val HIGH_ID_2 = "07000002-0000-0000-0000-000000000002"
        private const val HIGH_ID_3 = "07000003-0000-0000-0000-000000000003"
        private const val HIGH_ID_4 = "07000004-0000-0000-0000-000000000004"
        private const val OLDER_ID_1 = "06000001-0000-0000-0000-000000000001"
        private const val OLDER_ID_2 = "06000002-0000-0000-0000-000000000002"
        private const val OLDER_1_TEXT = "older-1"
        private const val OLDER_2_TEXT = "older-2"

        // Paging params
        private const val PAGE_SIZE = 2
        private const val OVERSIZED_PAGE = 5
        private const val LIVE_EDGE_POSITION = 0
        private const val ANCHOR_POSITION = 1

        // onHistoryLoaded invocation counts
        private const val NO_HISTORY_LOADS = 0
        private const val ONE_HISTORY_LOAD = 1
        private const val TWO_HISTORY_LOADS = 2
    }
}
