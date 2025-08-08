package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.entity.ChatEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.MessageEntity
import timur.gilfanov.messenger.data.source.local.database.entity.MessageType
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class ChatDaoTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val chatDao: ChatDao
        get() = databaseRule.chatDao

    private val participantDao: ParticipantDao
        get() = databaseRule.participantDao

    private val messageDao: MessageDao
        get() = databaseRule.messageDao

    @Test
    fun `insert and get chat by id`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val chat = createTestChat(chatId)

        // When
        chatDao.insertChat(chat)
        val result = chatDao.getChatById(chatId)

        // Then
        assertNotNull(result)
        assertEquals(chat.id, result.id)
        assertEquals(chat.name, result.name)
    }

    @Test
    fun `flow all chats returns all inserted chats`() = runTest {
        // Given
        val chat1 = createTestChat(UUID.randomUUID().toString())
        val chat2 = createTestChat(UUID.randomUUID().toString())
        val participant = createTestParticipant(UUID.randomUUID().toString())

        // When
        participantDao.insertParticipant(participant)
        chatDao.insertChat(chat1)
        chatDao.insertChat(chat2)
        chatDao.insertChatParticipantCrossRef(
            ChatParticipantCrossRef(chat1.id, participant.id),
        )
        chatDao.insertChatParticipantCrossRef(
            ChatParticipantCrossRef(chat2.id, participant.id),
        )

        val result = chatDao.flowAllChats().first()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == chat1.id })
        assertTrue(result.any { it.id == chat2.id })
    }

    @Test
    fun `flow chat with participants and messages emits updates`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val chat = createTestChat(chatId)
        val updatedChat = chat.copy(name = "Updated Name")

        // When
        chatDao.insertChat(chat)
        val initialResult = chatDao.flowChatWithParticipantsAndMessages(chatId).first()

        chatDao.updateChat(updatedChat)
        val updatedResult = chatDao.flowChatWithParticipantsAndMessages(chatId).first()

        // Then
        assertNotNull(initialResult)
        assertEquals(chat.name, initialResult.chat.name)
        assertNotNull(updatedResult)
        assertEquals("Updated Name", updatedResult.chat.name)
    }

    @Test
    fun `delete chat removes chat and cross references`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        val chat = createTestChat(chatId)
        val participant = createTestParticipant(participantId)
        val crossRef = ChatParticipantCrossRef(chatId, participantId)

        // When
        participantDao.insertParticipant(participant)
        chatDao.insertChat(chat)
        chatDao.insertChatParticipantCrossRef(crossRef)

        chatDao.deleteChat(chat)
        val result = chatDao.getChatById(chatId)

        // Then
        assertNull(result)
    }

    @Test
    fun `chat with participants and messages returns complete data`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val participantId = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()

        val chat = createTestChat(chatId)
        val participant = createTestParticipant(participantId)
        val message = createTestMessage(messageId, chatId, participantId)
        val crossRef = ChatParticipantCrossRef(chatId, participantId)

        // When
        participantDao.insertParticipant(participant)
        chatDao.insertChat(chat)
        messageDao.insertMessage(message)
        chatDao.insertChatParticipantCrossRef(crossRef)

        val result = chatDao.getChatWithParticipantsAndMessages(chatId)

        // Then
        assertNotNull(result)
        assertEquals(1, result.participants.size)
        assertEquals(participantId, result.participants.first().id)
        assertEquals(1, result.messages.size)
        assertEquals(messageId, result.messages.first().id)
    }

    @Test
    fun `update chat updates all fields`() = runTest {
        // Given
        val chatId = UUID.randomUUID().toString()
        val messageId = UUID.randomUUID().toString()
        val chat = createTestChat(chatId)
        val updatedChat = chat.copy(
            name = "Updated Name",
            lastReadMessageId = messageId,
            unreadMessagesCount = 5,
        )

        // When
        chatDao.insertChat(chat)
        chatDao.updateChat(updatedChat)
        val result = chatDao.getChatById(chatId)

        // Then
        assertNotNull(result)
        assertEquals("Updated Name", result.name)
        assertEquals(messageId, result.lastReadMessageId)
        assertEquals(5, result.unreadMessagesCount)
    }

    private fun createTestChat(id: String) = ChatEntity(
        id = id,
        name = "Test Chat",
        pictureUrl = null,
        rules = "[]",
        unreadMessagesCount = 0,
        lastReadMessageId = null,
        createdAt = Instant.fromEpochMilliseconds(1000000),
        updatedAt = Instant.fromEpochMilliseconds(1000000),
    )

    private fun createTestParticipant(id: String) = ParticipantEntity(
        id = id,
        name = "Test User",
        pictureUrl = null,
        joinedAt = Instant.fromEpochMilliseconds(900000),
        onlineAt = null,
        isAdmin = false,
        isModerator = false,
    )

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
