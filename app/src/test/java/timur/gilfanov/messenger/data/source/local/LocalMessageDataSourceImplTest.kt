package timur.gilfanov.messenger.data.source.local

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
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity
import timur.gilfanov.messenger.data.source.paging.MessageCursor
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class LocalMessageDataSourceImplTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val localMessageDataSource: LocalMessageDataSource by lazy {
        LocalMessageDataSourceImpl(
            messageDao = databaseRule.messageDao,
            chatDao = databaseRule.chatDao,
            database = databaseRule.database,
            logger = NoOpLogger(),
        )
    }

    @Before
    fun setup() = runTest {
        setupTestChatAndParticipant()
    }

    @Test
    fun `insert message successfully stores message`() = runTest {
        // Given
        val message = createTestMessage()

        // When
        val result = localMessageDataSource.insertMessage(message)

        // Then
        assertIs<ResultWithError.Success<Message, LocalDataSourceError>>(result)
        assertEquals(message, result.data)

        // Verify in database
        val storedMessage = databaseRule.messageDao.getMessageById(message.id.id.toString())
        assertNotNull(storedMessage)
        assertEquals(message.id.id.toString(), storedMessage.id)
        assertEquals(message.text, storedMessage.text)
    }

    @Test
    fun `update message successfully updates message data`() = runTest {
        // Given
        val originalMessage = createTestMessage()
        localMessageDataSource.insertMessage(originalMessage)

        val updatedMessage = originalMessage.copy(
            text = UPDATED_TEXT,
            editedAt = Instant.fromEpochMilliseconds(EDITED_AT),
        )

        // When
        val result = localMessageDataSource.updateMessage(updatedMessage)

        // Then
        assertIs<ResultWithError.Success<Message, LocalDataSourceError>>(result)
        assertEquals(updatedMessage, result.data)

        // Verify in database
        val storedMessage = databaseRule.messageDao.getMessageById(updatedMessage.id.id.toString())
        assertNotNull(storedMessage)
        assertEquals(UPDATED_TEXT, storedMessage.text)
        assertNotNull(storedMessage.editedAt)
    }

    @Test
    fun `delete message successfully removes message`() = runTest {
        // Given
        val message = createTestMessage()
        localMessageDataSource.insertMessage(message)

        // When
        val result = localMessageDataSource.deleteMessage(messageId = message.id)

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify message is removed from database
        val storedMessage = databaseRule.messageDao.getMessageById(message.id.id.toString())
        assertNull(storedMessage)
    }

    @Test
    fun `delete non-existent message returns MessageNotFound error`() = runTest {
        // Given
        val nonExistentMessageId = MessageId(UUID.fromString(NON_EXISTENT_MESSAGE_ID))

        // When
        val result = localMessageDataSource.deleteMessage(messageId = nonExistentMessageId)

        // Then
        assertIs<ResultWithError.Failure<Unit, LocalDataSourceError>>(result)
        assertIs<LocalDataSourceError.MessageNotFound>(result.error)
    }

    @Test
    fun `get message successfully retrieves message`() = runTest {
        // Given
        val message = createTestMessage()
        val insertMessage = localMessageDataSource.insertMessage(message)
        assertIs<ResultWithError.Success<Message, LocalDataSourceError>>(insertMessage)

        // When
        val result = localMessageDataSource.getMessage(message.id)

        // Then
        assertIs<ResultWithError.Success<Message, LocalDataSourceError>>(result)
        val retrievedMessage = result.data
        assertIs<TextMessage>(retrievedMessage)
        assertEquals(message.id, retrievedMessage.id)
        assertEquals(message.text, retrievedMessage.text)
        assertEquals(message.recipient, retrievedMessage.recipient)
    }

    @Test
    fun `get non-existent message returns MessageNotFound error`() = runTest {
        // Given
        val nonExistentMessageId = MessageId(UUID.fromString(NON_EXISTENT_MESSAGE_ID))

        // When
        val result = localMessageDataSource.getMessage(nonExistentMessageId)

        // Then
        assertIs<ResultWithError.Failure<Message, LocalDataSourceError>>(result)
        assertIs<LocalDataSourceError.MessageNotFound>(result.error)
    }

    @Test
    fun `message paging source refresh loads newest page first`() = runTest {
        insertThreeMessages()

        val result = localMessageDataSource.getMessagePagingSource(defaultChatId).load(
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
    fun `message paging source anchored refresh includes anchor with same-millisecond collision`() =
        runTest {
            // 4 messages at the same timestamp; the anchor has the lexicographically lowest id.
            // With the old `timestamp + 1` approach, the query returned `createdAt <= timestamp`
            // ordered by id DESC, so when more than pageSize messages share the timestamp the
            // low-id anchor was pushed off the first page.  The inclusive anchor query fixes this.
            val sameTimestamp = Instant.fromEpochMilliseconds(SAME_TIMESTAMP)
            val highIds = listOf(HIGH_ID_2, HIGH_ID_3, HIGH_ID_4)
            (listOf(ANCHOR_ID) + highIds).forEach { id ->
                localMessageDataSource.insertMessage(
                    createTestMessage(
                        id = id,
                        text = id,
                        createdAt = sameTimestamp,
                        sentAt = sameTimestamp,
                    ),
                )
            }
            // Two older messages to fill the page alongside the anchor.
            localMessageDataSource.insertMessage(
                createTestMessage(
                    id = OLDER_ID_1,
                    text = OLDER_1_TEXT,
                    createdAt = Instant.fromEpochMilliseconds(OLDER_TIMESTAMP),
                    sentAt = Instant.fromEpochMilliseconds(OLDER_TIMESTAMP),
                ),
            )
            localMessageDataSource.insertMessage(
                createTestMessage(
                    id = OLDER_ID_2,
                    text = OLDER_2_TEXT,
                    createdAt = Instant.fromEpochMilliseconds(OLDER_TIMESTAMP),
                    sentAt = Instant.fromEpochMilliseconds(OLDER_TIMESTAMP),
                ),
            )

            val result = localMessageDataSource.getMessagePagingSource(defaultChatId).load(
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
    fun `message paging source append loads older page`() = runTest {
        insertThreeMessages()

        val result = localMessageDataSource.getMessagePagingSource(defaultChatId).load(
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
    fun `message paging source prepend loads newer page in reverse order`() = runTest {
        insertThreeMessages()

        val result = localMessageDataSource.getMessagePagingSource(defaultChatId).load(
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
    fun `message paging source prepend returns null prevKey when fewer than page size`() = runTest {
        insertThreeMessages()

        val result = localMessageDataSource.getMessagePagingSource(defaultChatId).load(
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
    fun `message paging source prepend at live edge returns empty page`() = runTest {
        insertThreeMessages()

        val result = localMessageDataSource.getMessagePagingSource(defaultChatId).load(
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
        fun source() = localMessageDataSource.getMessagePagingSource(
            defaultChatId,
            onHistoryLoaded = { historyLoadedCount++ },
        )
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
        val source = localMessageDataSource.getMessagePagingSource(
            defaultChatId,
            isHistoryLoaded = { false },
        )

        assertNull(source.getRefreshKey(refreshKeyState(anchorPosition = ANCHOR_POSITION)))
    }

    @Test
    fun `getRefreshKey returns null at live edge`() {
        val source = localMessageDataSource.getMessagePagingSource(
            defaultChatId,
            isHistoryLoaded = { true },
        )

        assertNull(source.getRefreshKey(refreshKeyState(anchorPosition = LIVE_EDGE_POSITION)))
    }

    @Test
    fun `getRefreshKey returns null when anchor position is null`() {
        val source = localMessageDataSource.getMessagePagingSource(
            defaultChatId,
            isHistoryLoaded = { true },
        )

        assertNull(source.getRefreshKey(refreshKeyState(anchorPosition = null)))
    }

    @Test
    fun `getRefreshKey returns anchored cursor when history loaded`() {
        val source = localMessageDataSource.getMessagePagingSource(
            defaultChatId,
            isHistoryLoaded = { true },
        )

        val key = source.getRefreshKey(refreshKeyState(anchorPosition = ANCHOR_POSITION))

        assertEquals(
            MessageCursor(MIDDLE_TIMESTAMP, pagedMessageId(MIDDLE_SUFFIX)),
            key,
        )
    }

    @Test
    fun `insert message with parent successfully stores parent reference`() = runTest {
        // Given
        val parentMessage = createTestMessage(id = PARENT_MESSAGE_ID)
        val replyMessage = createTestMessage(
            id = REPLY_MESSAGE_ID,
            parentId = parentMessage.id,
        )

        localMessageDataSource.insertMessage(parentMessage)

        // When
        val result = localMessageDataSource.insertMessage(replyMessage)

        // Then
        assertIs<ResultWithError.Success<Message, LocalDataSourceError>>(result)

        // Verify parent reference in database
        val storedMessage = databaseRule.messageDao.getMessageById(replyMessage.id.id.toString())
        assertNotNull(storedMessage)
        assertEquals(parentMessage.id.id.toString(), storedMessage.parentId)
    }

    // Validation error tests
    @Test
    fun `insert message with blank text returns InvalidData error`() = runTest {
        // Given
        setupTestChatAndParticipant()
        val invalidMessage = createTestMessage().copy(text = "")

        // When
        val result = localMessageDataSource.insertMessage(invalidMessage)

        // Then
        assertIs<ResultWithError.Failure<Message, LocalDataSourceError>>(result)
        val invalidDataError0 = result.error
        assertIs<LocalDataSourceError.InvalidData>(invalidDataError0)
        assertEquals(FIELD_TEXT, invalidDataError0.field)
        assertEquals("Message text cannot be blank", invalidDataError0.reason)
    }

    @Test
    fun `insert message with text exceeding max length returns InvalidData error`() = runTest {
        // Given
        setupTestChatAndParticipant()
        val longText = "a".repeat(TextMessage.MAX_TEXT_LENGTH + 1)
        val invalidMessage = createTestMessage().copy(text = longText)

        // When
        val result = localMessageDataSource.insertMessage(invalidMessage)

        // Then
        assertIs<ResultWithError.Failure<Message, LocalDataSourceError>>(result)
        val invalidDataError1 = result.error
        assertIs<LocalDataSourceError.InvalidData>(invalidDataError1)
        assertEquals(FIELD_TEXT, invalidDataError1.field)
        assertTrue(invalidDataError1.reason.contains("cannot exceed"))
    }

    @Test
    fun `insert message with invalid timestamps returns InvalidData error`() = runTest {
        // Given
        setupTestChatAndParticipant()
        val invalidMessage = createTestMessage().copy(
            createdAt = Instant.fromEpochMilliseconds(INVALID_CREATED_AT),
            sentAt = Instant.fromEpochMilliseconds(INVALID_SENT_AT), // sentAt before createdAt
        )

        // When
        val result = localMessageDataSource.insertMessage(invalidMessage)

        // Then
        assertIs<ResultWithError.Failure<Message, LocalDataSourceError>>(result)
        val invalidDataError2 = result.error
        assertIs<LocalDataSourceError.InvalidData>(invalidDataError2)
        assertEquals(FIELD_TIMESTAMPS, invalidDataError2.field)
        assertEquals("Created time cannot be after sent time", invalidDataError2.reason)
    }

    @Test
    fun `update message with blank text returns InvalidData error`() = runTest {
        // Given
        setupTestChatAndParticipant()
        val originalMessage = createTestMessage()
        localMessageDataSource.insertMessage(originalMessage)

        val invalidMessage = originalMessage.copy(text = "")

        // When
        val result = localMessageDataSource.updateMessage(invalidMessage)

        // Then
        assertIs<ResultWithError.Failure<Message, LocalDataSourceError>>(result)
        val invalidDataError3 = result.error
        assertIs<LocalDataSourceError.InvalidData>(invalidDataError3)
        assertEquals(FIELD_TEXT, invalidDataError3.field)
    }

    // Upsert behavior tests (Room uses OnConflictStrategy.REPLACE)
    @Test
    fun `insert duplicate message successfully updates existing message`() = runTest {
        // Given
        setupTestChatAndParticipant()
        val message = createTestMessage()
        val insertResult = localMessageDataSource.insertMessage(message)
        assertIs<ResultWithError.Success<Message, LocalDataSourceError>>(insertResult)

        // When - insert same ID with different text
        val updatedMessage = message.copy(text = UPDATED_VIA_INSERT_TEXT)
        val result = localMessageDataSource.insertMessage(updatedMessage)

        // Then - verify it succeeded and updated
        assertIs<ResultWithError.Success<Message, LocalDataSourceError>>(result)
        assertEquals(updatedMessage, result.data)

        val storedMessage = databaseRule.messageDao.getMessageById(message.id.id.toString())
        assertNotNull(storedMessage)
        assertEquals(UPDATED_VIA_INSERT_TEXT, storedMessage.text)
    }

    private suspend fun setupTestChatAndParticipant() {
        // Insert test chat
        val chatEntity = ChatEntity(
            id = CHAT_ID,
            name = "Test Chat",
            pictureUrl = null,
            rules = "[]",
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            updatedAt = Instant.fromEpochMilliseconds(CHAT_UPDATED_AT),
        )
        databaseRule.chatDao.insertChat(chatEntity)

        // Insert test participant
        val participantEntity = ParticipantEntity(
            id = PARTICIPANT_ID,
            name = "Test Sender",
            pictureUrl = null,
            onlineAt = null,
        )
        databaseRule.participantDao.insertParticipant(participantEntity)

        // Create association between chat and participant
        databaseRule.chatDao.insertChatParticipantCrossRef(
            timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef(
                chatId = CHAT_ID,
                participantId = PARTICIPANT_ID,
                joinedAt = Instant.fromEpochMilliseconds(JOINED_AT),
                isAdmin = false,
                isModerator = false,
                isCurrentUser = true,
            ),
        )
    }

    private fun pagedMessageId(suffix: String) = "00000000-0000-0000-0000-0000000003$suffix"

    private suspend fun insertThreeMessages() {
        localMessageDataSource.insertMessage(
            createTestMessage(
                id = pagedMessageId(OLDEST_SUFFIX),
                text = OLDEST_TEXT,
                createdAt = Instant.fromEpochMilliseconds(OLDEST_TIMESTAMP),
                sentAt = Instant.fromEpochMilliseconds(OLDEST_TIMESTAMP),
            ),
        )
        localMessageDataSource.insertMessage(
            createTestMessage(
                id = pagedMessageId(MIDDLE_SUFFIX),
                text = MIDDLE_TEXT,
                createdAt = Instant.fromEpochMilliseconds(MIDDLE_TIMESTAMP),
                sentAt = Instant.fromEpochMilliseconds(MIDDLE_TIMESTAMP),
            ),
        )
        localMessageDataSource.insertMessage(
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

    private val defaultChatId = ChatId(UUID.fromString(CHAT_ID))

    private fun List<Message>.texts(): List<String> = map { message ->
        assertIs<TextMessage>(message).text
    }

    @Suppress("LongParameterList") // Test helper needs flexibility
    private fun createTestMessage(
        id: String = MESSAGE_ID,
        chatId: String = CHAT_ID,
        senderId: String = PARTICIPANT_ID,
        parentId: MessageId? = null,
        text: String = "Test message content",
        createdAt: Instant = Instant.fromEpochMilliseconds(MESSAGE_TIME),
        sentAt: Instant? = Instant.fromEpochMilliseconds(MESSAGE_TIME),
        deliveredAt: Instant? = null,
    ) = TextMessage(
        id = MessageId(UUID.fromString(id)),
        recipient = ChatId(UUID.fromString(chatId)),
        sender = Participant(
            id = ParticipantId(UUID.fromString(senderId)),
            name = "Test Sender",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(JOINED_AT),
            onlineAt = null,
            isAdmin = false,
            isModerator = false,
        ),
        parentId = parentId,
        text = text,
        deliveryStatus = DeliveryStatus.Sent,
        createdAt = createdAt,
        sentAt = sentAt,
        deliveredAt = deliveredAt,
        editedAt = null,
    )

    companion object {
        // Entity ids
        private const val CHAT_ID = "11111111-1111-1111-1111-111111111111"
        private const val PARTICIPANT_ID = "22222222-2222-2222-2222-222222222222"
        private const val MESSAGE_ID = "33333333-3333-3333-3333-333333333333"
        private const val NON_EXISTENT_MESSAGE_ID = "99999999-9999-9999-9999-999999999999"
        private const val PARENT_MESSAGE_ID = "5555555-5555-5555-5555-555555555555"
        private const val REPLY_MESSAGE_ID = "66666666-6666-6666-6666-666666666666"

        // Timestamps (epoch millis)
        private const val JOINED_AT = 900000L
        private const val CHAT_UPDATED_AT = 1000000L
        private const val MESSAGE_TIME = 1500000L
        private const val EDITED_AT = 2000000L
        private const val INVALID_CREATED_AT = 2000000L
        private const val INVALID_SENT_AT = 1500000L

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

        // Message texts
        private const val UPDATED_TEXT = "Updated message text"
        private const val UPDATED_VIA_INSERT_TEXT = "Updated message text via insert"

        // Validation error field names
        private const val FIELD_TEXT = "text"
        private const val FIELD_TIMESTAMPS = "timestamps"

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
