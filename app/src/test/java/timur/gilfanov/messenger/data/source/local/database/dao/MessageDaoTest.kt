package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
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
import timur.gilfanov.messenger.data.source.local.database.entity.MessageEntity
import timur.gilfanov.messenger.data.source.local.database.entity.MessageType
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class MessageDaoTest {

    private lateinit var database: MessengerDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var chatDao: ChatDao
    private lateinit var participantDao: ParticipantDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MessengerDatabase::class.java,
        ).allowMainThreadQueries().build()

        messageDao = database.messageDao()
        chatDao = database.chatDao()
        participantDao = database.participantDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert and get message by id`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()

        setupChatAndParticipant(chatId, participantId)
        val message = createTestMessage(messageId, chatId, participantId)

        // When
        messageDao.insertMessage(message)
        val result = messageDao.getMessageById(messageId)

        // Then
        assertNotNull(result)
        assertEquals(message.id, result.id)
        assertEquals(message.text, result.text)
    }

    @Test
    fun `get messages by chat id returns all chat messages`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        setupChatAndParticipant(chatId, participantId)

        val message1 = createTestMessage(UUID.randomUUID().toString(), chatId, participantId)
        val message2 = createTestMessage(UUID.randomUUID().toString(), chatId, participantId)

        // When
        messageDao.insertMessage(message1)
        messageDao.insertMessage(message2)
        val result = messageDao.getMessagesByChatId(chatId)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == message1.id })
        assertTrue(result.any { it.id == message2.id })
    }

    @Test
    fun `flow messages by chat id emits updates`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        setupChatAndParticipant(chatId, participantId)

        val message1 = createTestMessage(UUID.randomUUID().toString(), chatId, participantId)
        val message2 = createTestMessage(UUID.randomUUID().toString(), chatId, participantId)

        // When
        messageDao.insertMessage(message1)
        val initialResult = messageDao.flowMessagesByChatId(chatId).first()

        messageDao.insertMessage(message2)
        val updatedResult = messageDao.flowMessagesByChatId(chatId).first()

        // Then
        assertEquals(1, initialResult.size)
        assertEquals(2, updatedResult.size)
    }

    @Test
    fun `update message updates text and edited time`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()
        setupChatAndParticipant(chatId, participantId)

        val message = createTestMessage(messageId, chatId, participantId)
        val updatedMessage = message.copy(
            text = "Updated text",
            editedAt = Instant.fromEpochMilliseconds(1200000),
        )

        // When
        messageDao.insertMessage(message)
        messageDao.updateMessage(updatedMessage)
        val result = messageDao.getMessageById(messageId)

        // Then
        assertNotNull(result)
        assertEquals("Updated text", result.text)
        assertNotNull(result.editedAt)
    }

    @Test
    fun `delete message removes message from database`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()
        setupChatAndParticipant(chatId, participantId)

        val message = createTestMessage(messageId, chatId, participantId)

        // When
        messageDao.insertMessage(message)
        messageDao.deleteMessage(message)
        val result = messageDao.getMessageById(messageId)

        // Then
        assertNull(result)
    }

    @Test
    fun `get last message in chat returns most recent message`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        setupChatAndParticipant(chatId, participantId)

        val oldMessage = createTestMessage(
            UUID.randomUUID().toString(),
            chatId,
            participantId,
        ).copy(createdAt = Instant.fromEpochMilliseconds(500000))

        val newMessage = createTestMessage(
            UUID.randomUUID().toString(),
            chatId,
            participantId,
        ).copy(createdAt = Instant.fromEpochMilliseconds(1500000))

        // When
        messageDao.insertMessage(oldMessage)
        messageDao.insertMessage(newMessage)
        val result = messageDao.getLastMessageInChat(chatId)

        // Then
        assertNotNull(result)
        assertEquals(newMessage.id, result.id)
    }

    @Test
    fun `update delivery status updates only status field`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()
        setupChatAndParticipant(chatId, participantId)

        val message = createTestMessage(messageId, chatId, participantId)
        val newStatus = """{"type":"Sent"}"""

        // When
        messageDao.insertMessage(message)
        messageDao.updateMessageDeliveryStatus(messageId, newStatus)
        val result = messageDao.getMessageById(messageId)

        // Then
        assertNotNull(result)
        assertEquals(newStatus, result.deliveryStatus)
        assertEquals(message.text, result.text) // Other fields unchanged
    }

    @Test
    fun `messages with parent id are properly linked`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        val parentMessageId = UUID.randomUUID().toString()
        val replyMessageId = UUID.randomUUID().toString()
        setupChatAndParticipant(chatId, participantId)

        val parentMessage = createTestMessage(parentMessageId, chatId, participantId)
        val replyMessage = createTestMessage(replyMessageId, chatId, participantId).copy(
            parentId = parentMessageId,
        )

        // When
        messageDao.insertMessage(parentMessage)
        messageDao.insertMessage(replyMessage)
        val result = messageDao.getMessageById(replyMessageId)

        // Then
        assertNotNull(result)
        assertEquals(parentMessageId, result.parentId)
    }

    private suspend fun setupChatAndParticipant(chatId: String, participantId: String) {
        val chat = ChatEntity(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            rules = "[]",
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            createdAt = Instant.fromEpochMilliseconds(1000000),
            updatedAt = Instant.fromEpochMilliseconds(1000000),
        )
        val participant = ParticipantEntity(
            id = participantId,
            name = "Test User",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(900000),
            onlineAt = null,
            isAdmin = false,
            isModerator = false,
        )
        chatDao.insertChat(chat)
        participantDao.insertParticipant(participant)
    }

    private fun createTestMessage(id: String, chatId: String, senderId: String) = MessageEntity(
        id = id,
        chatId = chatId,
        senderId = senderId,
        parentId = null,
        type = MessageType.TEXT,
        text = "Test message",
        imageUrl = null,
        deliveryStatus = null,
        createdAt = Instant.fromEpochMilliseconds(1100000),
        sentAt = null,
        deliveredAt = null,
        editedAt = null,
        updatedAt = Instant.fromEpochMilliseconds(1100000),
    )
}
