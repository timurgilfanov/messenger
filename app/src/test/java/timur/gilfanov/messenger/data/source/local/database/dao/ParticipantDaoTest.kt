package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.entity.ChatEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.MessageEntity
import timur.gilfanov.messenger.data.source.local.database.entity.MessageType
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class ParticipantDaoTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val participantDao: ParticipantDao
        get() = databaseRule.participantDao

    private val chatDao: ChatDao
        get() = databaseRule.chatDao

    private val messageDao: MessageDao
        get() = databaseRule.messageDao

    @Test
    fun `insert and get participant by id`() = runTest {
        // Given
        val participantId = PARTICIPANT_ID
        val participant = createTestParticipant(participantId)

        // When
        participantDao.insertParticipant(participant)
        val result = participantDao.getParticipantById(participantId)

        // Then
        assertNotNull(result)
        assertEquals(participant.id, result.id)
        assertEquals(participant.name, result.name)
    }

    @Test
    fun `insert multiple participants and get all`() = runTest {
        // Given
        val participant1 = createTestParticipant(PARTICIPANT_ID, "User 1")
        val participant2 = createTestParticipant(SECOND_PARTICIPANT_ID, "User 2")
        val participants = listOf(participant1, participant2)

        // When
        participantDao.insertParticipants(participants)
        val result = participantDao.getAllParticipants()

        // Then
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == participant1.id })
        assertTrue(result.any { it.id == participant2.id })
    }

    @Test
    fun `update participant changes data through different methods`() = runTest {
        // Given
        val participantId = PARTICIPANT_ID
        val participant = createTestParticipant(participantId, "Original Name")
        val onlineTime = Instant.fromEpochMilliseconds(2000000)

        // When - Insert initial participant
        participantDao.insertParticipant(participant)
        val initialResult = participantDao.getParticipantById(participantId)

        // Update via updateParticipant method
        val updatedParticipant = participant.copy(
            name = "Updated Name",
            onlineAt = onlineTime,
        )
        participantDao.updateParticipant(updatedParticipant)
        val updatedResult = participantDao.getParticipantById(participantId)

        // Update via updateParticipantOnlineStatus method
        val newOnlineTime = Instant.fromEpochMilliseconds(2500000).toEpochMilliseconds()
        participantDao.updateParticipantOnlineStatus(participantId, newOnlineTime)
        val finalResult = participantDao.getParticipantById(participantId)

        // Then - Verify initial state
        assertNotNull(initialResult)
        assertEquals("Original Name", initialResult.name)
        assertNull(initialResult.onlineAt)

        // Verify updateParticipant worked
        assertNotNull(updatedResult)
        assertEquals("Updated Name", updatedResult.name)
        assertEquals(onlineTime, updatedResult.onlineAt)

        // Verify updateParticipantOnlineStatus worked
        assertNotNull(finalResult)
        assertEquals("Updated Name", finalResult.name) // Name should remain
        assertEquals(newOnlineTime, finalResult.onlineAt?.toEpochMilliseconds())
    }

    @Test
    fun `delete participant removes from database`() = runTest {
        // Given
        val participantId = PARTICIPANT_ID
        val participant = createTestParticipant(participantId)

        // When
        participantDao.insertParticipant(participant)
        participantDao.deleteParticipant(participant)
        val result = participantDao.getParticipantById(participantId)

        // Then
        assertNull(result)
    }

    @Test
    fun `participant with admin role is properly stored via cross-reference`() = runTest {
        // Given
        val participantId = PARTICIPANT_ID
        val chatId = CHAT_ID
        val participant = createTestParticipant(participantId)

        // Create test chat first
        val chat = ChatEntity(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            rules = "[]",
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            updatedAt = Instant.fromEpochMilliseconds(1000000),
        )

        val crossRef = ChatParticipantCrossRef(
            chatId = chatId,
            participantId = participantId,
            joinedAt = Instant.fromEpochMilliseconds(900000),
            isAdmin = true,
            isModerator = false,
        )

        // When
        participantDao.insertParticipant(participant)
        chatDao.insertChat(chat)
        chatDao.insertChatParticipantCrossRef(crossRef)

        val chatWithDetails = chatDao.getChatWithParticipantsAndMessages(chatId)

        // Then
        assertNotNull(chatWithDetails)
        val adminCrossRef = chatWithDetails!!.participantCrossRefs.find {
            it.participantId ==
                participantId
        }
        assertNotNull(adminCrossRef)
        assertEquals(true, adminCrossRef!!.isAdmin)
        assertEquals(false, adminCrossRef.isModerator)
    }

    @Test
    fun `participant with moderator role is properly stored via cross-reference`() = runTest {
        // Given
        val participantId = PARTICIPANT_ID
        val chatId = CHAT_ID
        val participant = createTestParticipant(participantId)

        // Create test chat first
        val chat = ChatEntity(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            rules = "[]",
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            updatedAt = Instant.fromEpochMilliseconds(1000000),
        )

        val crossRef = ChatParticipantCrossRef(
            chatId = chatId,
            participantId = participantId,
            joinedAt = Instant.fromEpochMilliseconds(900000),
            isAdmin = false,
            isModerator = true,
        )

        // When
        participantDao.insertParticipant(participant)
        chatDao.insertChat(chat)
        chatDao.insertChatParticipantCrossRef(crossRef)

        val chatWithDetails = chatDao.getChatWithParticipantsAndMessages(chatId)

        // Then
        assertNotNull(chatWithDetails)
        val moderatorCrossRef = chatWithDetails!!.participantCrossRefs.find {
            it.participantId ==
                participantId
        }
        assertNotNull(moderatorCrossRef)
        assertEquals(false, moderatorCrossRef!!.isAdmin)
        assertEquals(true, moderatorCrossRef.isModerator)
    }

    @Test
    fun `deleteOrphanedParticipants returns zero when all participants are referenced`() = runTest {
        // Given - two participants, both linked to a chat
        val participantA = createTestParticipant(PARTICIPANT_ID, "User A")
        val participantB = createTestParticipant(SECOND_PARTICIPANT_ID, "User B")
        val chatId = CHAT_ID
        val chat = createTestChat(chatId)
        participantDao.insertParticipants(listOf(participantA, participantB))
        chatDao.insertChat(chat)
        chatDao.insertChatParticipantCrossRefs(
            listOf(
                createCrossRef(chatId, participantA.id),
                createCrossRef(chatId, participantB.id),
            ),
        )

        // When
        val deleted = participantDao.deleteOrphanedParticipants()

        // Then
        assertEquals(0, deleted)
        val remaining = participantDao.getAllParticipants()
        assertEquals(2, remaining.size)
    }

    @Test
    fun `deleteOrphanedParticipants removes participant whose only chat reference was deleted`() =
        runTest {
            // Given - participant linked to a chat, then the junction row is removed
            val participantId = PARTICIPANT_ID
            val participant = createTestParticipant(participantId)
            val chatId = CHAT_ID
            val chat = createTestChat(chatId)
            participantDao.insertParticipant(participant)
            chatDao.insertChat(chat)
            chatDao.insertChatParticipantCrossRef(createCrossRef(chatId, participantId))
            chatDao.removeAllChatParticipants(chatId)

            // When
            val deleted = participantDao.deleteOrphanedParticipants()

            // Then
            assertEquals(1, deleted)
            assertNull(participantDao.getParticipantById(participantId))
        }

    @Test
    fun `deleteOrphanedParticipants preserves participants still referenced by another chat`() =
        runTest {
            // Given - one participant in two chats, one chat is removed
            val sharedParticipantId = PARTICIPANT_ID
            val orphanParticipantId = SECOND_PARTICIPANT_ID
            val sharedParticipant = createTestParticipant(sharedParticipantId, "Shared")
            val orphanParticipant = createTestParticipant(orphanParticipantId, "Orphan")
            val chatA = createTestChat(CHAT_ID)
            val chatB = createTestChat(SECOND_CHAT_ID)
            participantDao.insertParticipants(listOf(sharedParticipant, orphanParticipant))
            chatDao.insertChat(chatA)
            chatDao.insertChat(chatB)
            chatDao.insertChatParticipantCrossRefs(
                listOf(
                    createCrossRef(chatA.id, sharedParticipantId),
                    createCrossRef(chatB.id, sharedParticipantId),
                    createCrossRef(chatA.id, orphanParticipantId),
                ),
            )
            // Remove chatA — junction rows for chatA cascade away.
            chatDao.deleteChat(chatA)

            // When
            val deleted = participantDao.deleteOrphanedParticipants()

            // Then - shared participant stays (still in chatB), orphan is removed
            assertEquals(1, deleted)
            assertNotNull(participantDao.getParticipantById(sharedParticipantId))
            assertNull(participantDao.getParticipantById(orphanParticipantId))
        }

    @Test
    fun `deleteOrphanedParticipants preserves participant still referenced by messages`() =
        runTest {
            val participantId = PARTICIPANT_ID
            val chatId = CHAT_ID
            val messageId = MESSAGE_ID
            val participant = createTestParticipant(participantId)
            val chat = createTestChat(chatId)
            val message = createTestMessage(messageId, chatId, participantId)

            participantDao.insertParticipant(participant)
            chatDao.insertChat(chat)
            chatDao.insertChatParticipantCrossRef(createCrossRef(chatId, participantId))
            messageDao.insertMessage(message)
            chatDao.removeAllChatParticipants(chatId)

            val deleted = participantDao.deleteOrphanedParticipants()

            assertEquals(0, deleted)
            assertNotNull(participantDao.getParticipantById(participantId))
            assertNotNull(messageDao.getMessageById(messageId))
        }

    @Test
    fun `deleteOrphanedParticipants returns zero when participants table is empty`() = runTest {
        // When
        val deleted = participantDao.deleteOrphanedParticipants()

        // Then
        assertEquals(0, deleted)
        assertTrue(participantDao.getAllParticipants().isEmpty())
    }

    private fun createTestParticipant(id: String, name: String = "Test User") = ParticipantEntity(
        id = id,
        name = name,
        pictureUrl = null,
        onlineAt = null,
    )

    private fun createTestChat(id: String) = ChatEntity(
        id = id,
        name = "Test Chat",
        pictureUrl = null,
        rules = "[]",
        unreadMessagesCount = 0,
        lastReadMessageId = null,
        updatedAt = Instant.fromEpochMilliseconds(1000000),
    )

    private fun createCrossRef(chatId: String, participantId: String) = ChatParticipantCrossRef(
        chatId = chatId,
        participantId = participantId,
        joinedAt = Instant.fromEpochMilliseconds(900000),
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
    )

    private companion object {
        const val PARTICIPANT_ID = "11111111-1111-1111-1111-111111111111"
        const val SECOND_PARTICIPANT_ID = "22222222-2222-2222-2222-222222222222"
        const val CHAT_ID = "33333333-3333-3333-3333-333333333333"
        const val SECOND_CHAT_ID = "44444444-4444-4444-4444-444444444444"
        const val MESSAGE_ID = "55555555-5555-5555-5555-555555555555"
    }
}
