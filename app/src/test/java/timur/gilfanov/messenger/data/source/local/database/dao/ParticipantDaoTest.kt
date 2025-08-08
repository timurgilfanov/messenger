package timur.gilfanov.messenger.data.source.local.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Component
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
    fun `update participant changes data`() = runTest {
        // Given
        val participantId = UUID.randomUUID().toString()
        val participant = createTestParticipant(participantId)
        val updatedParticipant = participant.copy(
            name = "Updated Name",
            onlineAt = Instant.fromEpochMilliseconds(2500000),
        )

        // When
        participantDao.insertParticipant(participant)
        val initialResult = participantDao.getParticipantById(participantId)

        participantDao.updateParticipant(updatedParticipant)
        val updatedResult = participantDao.getParticipantById(participantId)

        // Then
        assertNotNull(initialResult)
        assertEquals(participant.name, initialResult.name)
        assertNull(initialResult.onlineAt)

        assertNotNull(updatedResult)
        assertEquals("Updated Name", updatedResult.name)
        assertNotNull(updatedResult.onlineAt)
    }

    @Test
    fun `update participant online status`() = runTest {
        // Given
        val participantId = UUID.randomUUID().toString()
        val participant = createTestParticipant(participantId)
        val onlineTime = Instant.fromEpochMilliseconds(2000000).toEpochMilliseconds()

        // When
        participantDao.insertParticipant(participant)
        participantDao.updateParticipantOnlineStatus(participantId, onlineTime)
        val result = participantDao.getParticipantById(participantId)

        // Then
        assertNotNull(result)
        assertEquals(onlineTime, result.onlineAt?.toEpochMilliseconds())
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
    fun `participant with admin role is properly stored`() = runTest {
        // Given
        val participantId = UUID.randomUUID().toString()
        val participant = createTestParticipant(participantId).copy(
            isAdmin = true,
            isModerator = false,
        )

        // When
        participantDao.insertParticipant(participant)
        val result = participantDao.getParticipantById(participantId)

        // Then
        assertNotNull(result)
        assertEquals(true, result.isAdmin)
        assertEquals(false, result.isModerator)
    }

    @Test
    fun `participant with moderator role is properly stored`() = runTest {
        // Given
        val participantId = UUID.randomUUID().toString()
        val participant = createTestParticipant(participantId).copy(
            isAdmin = false,
            isModerator = true,
        )

        // When
        participantDao.insertParticipant(participant)
        val result = participantDao.getParticipantById(participantId)

        // Then
        assertNotNull(result)
        assertEquals(false, result.isAdmin)
        assertEquals(true, result.isModerator)
    }

    @Test
    fun `upsert participant updates existing entry`() = runTest {
        // Given
        val participantId = UUID.randomUUID().toString()
        val participant = createTestParticipant(participantId, "Original Name")
        val updatedParticipant = participant.copy(name = "Updated Name")

        // When
        participantDao.insertParticipant(participant)
        participantDao.insertParticipant(updatedParticipant) // Upsert
        val result = participantDao.getParticipantById(participantId)

        // Then
        assertNotNull(result)
        assertEquals("Updated Name", result.name)
    }

    private fun createTestParticipant(id: String, name: String = "Test User") = ParticipantEntity(
        id = id,
        name = name,
        pictureUrl = null,
        joinedAt = Instant.fromEpochMilliseconds(900000),
        onlineAt = null,
        isAdmin = false,
        isModerator = false,
    )
}
