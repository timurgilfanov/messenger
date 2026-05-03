package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
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

    @Test
    fun `insert and get participant by id`() = runTest {
        // Given
        val participantId = UUID.randomUUID().toString()
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
        val participant1 = createTestParticipant(UUID.randomUUID().toString(), "User 1")
        val participant2 = createTestParticipant(UUID.randomUUID().toString(), "User 2")
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
        val participantId = UUID.randomUUID().toString()
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
        val participantId = UUID.randomUUID().toString()
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
        val participantId = UUID.randomUUID().toString()
        val chatId = UUID.randomUUID().toString()
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
        val participantId = UUID.randomUUID().toString()
        val chatId = UUID.randomUUID().toString()
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
        val participantA = createTestParticipant(UUID.randomUUID().toString(), "User A")
        val participantB = createTestParticipant(UUID.randomUUID().toString(), "User B")
        val chatId = UUID.randomUUID().toString()
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
            val participantId = UUID.randomUUID().toString()
            val participant = createTestParticipant(participantId)
            val chatId = UUID.randomUUID().toString()
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
            val sharedParticipantId = UUID.randomUUID().toString()
            val orphanParticipantId = UUID.randomUUID().toString()
            val sharedParticipant = createTestParticipant(sharedParticipantId, "Shared")
            val orphanParticipant = createTestParticipant(orphanParticipantId, "Orphan")
            val chatA = createTestChat(UUID.randomUUID().toString())
            val chatB = createTestChat(UUID.randomUUID().toString())
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
}
