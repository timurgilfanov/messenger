package timur.gilfanov.messenger.data.source.local

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
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule
import timur.gilfanov.messenger.util.NoOpLogger

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
            text = "Updated message text",
            editedAt = Instant.fromEpochMilliseconds(2000000),
        )

        // When
        val result = localMessageDataSource.updateMessage(updatedMessage)

        // Then
        assertIs<ResultWithError.Success<Message, LocalDataSourceError>>(result)
        assertEquals(updatedMessage, result.data)

        // Verify in database
        val storedMessage = databaseRule.messageDao.getMessageById(updatedMessage.id.id.toString())
        assertNotNull(storedMessage)
        assertEquals("Updated message text", storedMessage.text)
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
        val nonExistentMessageId =
            MessageId(UUID.fromString("99999999-9999-9999-9999-999999999999"))

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
        val nonExistentMessageId =
            MessageId(UUID.fromString("99999999-9999-9999-9999-999999999999"))

        // When
        val result = localMessageDataSource.getMessage(nonExistentMessageId)

        // Then
        assertIs<ResultWithError.Failure<Message, LocalDataSourceError>>(result)
        assertIs<LocalDataSourceError.MessageNotFound>(result.error)
    }

    @Test
    fun `insert message with parent successfully stores parent reference`() = runTest {
        // Given
        val parentMessage = createTestMessage(id = "5555555-5555-5555-5555-555555555555")
        val replyMessage = createTestMessage(
            id = "66666666-6666-6666-6666-666666666666",
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
        assertIs<LocalDataSourceError.InvalidData>(result.error)
        assertEquals("text", result.error.field)
        assertEquals("Message text cannot be blank", result.error.reason)
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
        assertIs<LocalDataSourceError.InvalidData>(result.error)
        assertEquals("text", result.error.field)
        assertTrue(result.error.reason.contains("cannot exceed"))
    }

    @Test
    fun `insert message with invalid timestamps returns InvalidData error`() = runTest {
        // Given
        setupTestChatAndParticipant()
        val invalidMessage = createTestMessage().copy(
            createdAt = Instant.fromEpochMilliseconds(2000000),
            sentAt = Instant.fromEpochMilliseconds(1500000), // sentAt before createdAt
        )

        // When
        val result = localMessageDataSource.insertMessage(invalidMessage)

        // Then
        assertIs<ResultWithError.Failure<Message, LocalDataSourceError>>(result)
        assertIs<LocalDataSourceError.InvalidData>(result.error)
        assertEquals("timestamps", result.error.field)
        assertEquals("Created time cannot be after sent time", result.error.reason)
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
        assertIs<LocalDataSourceError.InvalidData>(result.error)
        assertEquals("text", result.error.field)
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
        val updatedMessage = message.copy(text = "Updated message text via insert")
        val result = localMessageDataSource.insertMessage(updatedMessage)

        // Then - verify it succeeded and updated
        assertIs<ResultWithError.Success<Message, LocalDataSourceError>>(result)
        assertEquals(updatedMessage, result.data)

        val storedMessage = databaseRule.messageDao.getMessageById(message.id.id.toString())
        assertNotNull(storedMessage)
        assertEquals("Updated message text via insert", storedMessage.text)
    }

    private suspend fun setupTestChatAndParticipant() {
        // Insert test chat
        val chatEntity = ChatEntity(
            id = "11111111-1111-1111-1111-111111111111", // Fixed UUID to match message
            name = "Test Chat",
            pictureUrl = null,
            rules = "[]",
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            updatedAt = Instant.fromEpochMilliseconds(1000000),
        )
        databaseRule.chatDao.insertChat(chatEntity)

        // Insert test participant
        val participantEntity = ParticipantEntity(
            id = "22222222-2222-2222-2222-222222222222", // Fixed UUID to match message
            name = "Test Sender",
            pictureUrl = null,
            onlineAt = null,
        )
        databaseRule.participantDao.insertParticipant(participantEntity)

        // Create association between chat and participant
        databaseRule.chatDao.insertChatParticipantCrossRef(
            timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef(
                chatId = "11111111-1111-1111-1111-111111111111",
                participantId = "22222222-2222-2222-2222-222222222222",
                joinedAt = Instant.fromEpochMilliseconds(900000),
                isAdmin = false,
                isModerator = false,
            ),
        )
    }

    @Suppress("LongParameterList") // Test helper needs flexibility
    private fun createTestMessage(
        id: String = "33333333-3333-3333-3333-333333333333",
        chatId: String = "11111111-1111-1111-1111-111111111111",
        senderId: String = "22222222-2222-2222-2222-222222222222",
        parentId: MessageId? = null,
        text: String = "Test message content",
        createdAt: Instant = Instant.fromEpochMilliseconds(1500000),
        sentAt: Instant? = Instant.fromEpochMilliseconds(1500000),
        deliveredAt: Instant? = null,
    ) = TextMessage(
        id = MessageId(UUID.fromString(id)),
        recipient = ChatId(UUID.fromString(chatId)),
        sender = Participant(
            id = ParticipantId(UUID.fromString(senderId)),
            name = "Test Sender",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(900000),
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
}
