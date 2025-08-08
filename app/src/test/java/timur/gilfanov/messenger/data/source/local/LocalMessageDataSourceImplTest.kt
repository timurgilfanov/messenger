package timur.gilfanov.messenger.data.source.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
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
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class LocalMessageDataSourceImplTest {

    private lateinit var database: MessengerDatabase
    private lateinit var localMessageDataSource: LocalMessageDataSource

    @Before
    fun setup() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MessengerDatabase::class.java,
        ).allowMainThreadQueries().build()

        localMessageDataSource = LocalMessageDataSourceImpl(
            messageDao = database.messageDao(),
            chatDao = database.chatDao(),
            database = database,
        )

        // Setup required chat and participant for foreign key constraints
        setupTestChatAndParticipant()
    }

    @After
    fun tearDown() {
        database.close()
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
        val storedMessage = database.messageDao().getMessageById(message.id.id.toString())
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
        val storedMessage = database.messageDao().getMessageById(updatedMessage.id.id.toString())
        assertNotNull(storedMessage)
        assertEquals("Updated message text", storedMessage.text)
        assertNotNull(storedMessage.editedAt)
    }

    @Test
    fun `delete message FOR_SENDER successfully removes message`() = runTest {
        // Given
        val message = createTestMessage()
        localMessageDataSource.insertMessage(message)

        // When
        val result = localMessageDataSource.deleteMessage(
            messageId = message.id,
            mode = DeleteMessageMode.FOR_SENDER_ONLY,
        )

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify message is removed from database
        val storedMessage = database.messageDao().getMessageById(message.id.id.toString())
        assertNull(storedMessage)
    }

    @Test
    fun `delete message FOR_EVERYONE successfully removes message`() = runTest {
        // Given
        val message = createTestMessage()
        localMessageDataSource.insertMessage(message)

        // When
        val result = localMessageDataSource.deleteMessage(
            messageId = message.id,
            mode = DeleteMessageMode.FOR_EVERYONE,
        )

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify message is removed from database
        val storedMessage = database.messageDao().getMessageById(message.id.id.toString())
        assertNull(storedMessage)
    }

    @Test
    fun `delete non-existent message returns MessageNotFound error`() = runTest {
        // Given
        val nonExistentMessageId =
            MessageId(UUID.fromString("99999999-9999-9999-9999-999999999999"))

        // When
        val result = localMessageDataSource.deleteMessage(
            messageId = nonExistentMessageId,
            mode = DeleteMessageMode.FOR_SENDER_ONLY,
        )

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
        val storedMessage = database.messageDao().getMessageById(replyMessage.id.id.toString())
        assertNotNull(storedMessage)
        assertEquals(parentMessage.id.id.toString(), storedMessage.parentId)
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
            createdAt = Instant.fromEpochMilliseconds(1000000),
            updatedAt = Instant.fromEpochMilliseconds(1000000),
        )
        database.chatDao().insertChat(chatEntity)

        // Insert test participant
        val participantEntity = ParticipantEntity(
            id = "22222222-2222-2222-2222-222222222222", // Fixed UUID to match message
            name = "Test Sender",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(900000),
            onlineAt = null,
            isAdmin = false,
            isModerator = false,
        )
        database.participantDao().insertParticipant(participantEntity)

        // Create association between chat and participant
        database.chatDao().insertChatParticipantCrossRef(
            timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef(
                chatId = "11111111-1111-1111-1111-111111111111",
                participantId = "22222222-2222-2222-2222-222222222222",
            ),
        )
    }

    private fun createTestMessage(
        id: String = "33333333-3333-3333-3333-333333333333",
        parentId: MessageId? = null,
    ) = TextMessage(
        id = MessageId(UUID.fromString(id)),
        recipient = ChatId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
        sender = Participant(
            id = ParticipantId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
            name = "Test Sender",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(900000),
            onlineAt = null,
            isAdmin = false,
            isModerator = false,
        ),
        parentId = parentId,
        text = "Test message content",
        deliveryStatus = DeliveryStatus.Sent,
        createdAt = Instant.fromEpochMilliseconds(1500000),
        sentAt = Instant.fromEpochMilliseconds(1500000),
        deliveredAt = null,
        editedAt = null,
    )
}
