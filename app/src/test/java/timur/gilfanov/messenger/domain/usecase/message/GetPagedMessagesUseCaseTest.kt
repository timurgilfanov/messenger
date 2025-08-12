package timur.gilfanov.messenger.domain.usecase.message

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.annotations.Unit
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.testutil.DomainTestFixtures

/**
 * Unit tests for GetPagedMessagesUseCase.
 *
 * Tests the use case integration with pagination without requiring
 * complex database setup or real PagingSource implementation.
 */
@Category(Unit::class)
class GetPagedMessagesUseCaseTest {

    private lateinit var fakeRepository: MessageRepositoryFake
    private lateinit var useCase: GetPagedMessagesUseCase
    private val testChatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val testUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))

    @Before
    fun setUp() {
        fakeRepository = MessageRepositoryFake()
        useCase = GetPagedMessagesUseCase(fakeRepository)
    }

    @Test
    fun `invoke returns flow of PagingData from repository`() = runTest {
        // Given: Repository with test messages
        val testMessages = listOf(
            createTestMessage("Message 1"),
            createTestMessage("Message 2"),
            createTestMessage("Message 3"),
        )
        fakeRepository.messages = testMessages

        // When: Invoking use case
        val pagingFlow = useCase(testChatId)
        val snapshot = pagingFlow.asSnapshot()

        // Then: Returns paginated messages from repository
        assertEquals(3, snapshot.size)
        assertEquals("Message 1", (snapshot[0] as TextMessage).text)
        assertEquals("Message 2", (snapshot[1] as TextMessage).text)
        assertEquals("Message 3", (snapshot[2] as TextMessage).text)
    }

    @Test
    fun `invoke handles empty data gracefully`() = runTest {
        // Given: Repository with no messages
        fakeRepository.messages = emptyList()

        // When: Invoking use case
        val pagingFlow = useCase(testChatId)
        val snapshot = pagingFlow.asSnapshot()

        // Then: Returns empty list
        assertTrue(snapshot.isEmpty())
    }

    @Test
    fun `invoke passes correct chat ID to repository`() = runTest {
        // Given: Repository that tracks method calls
        val specificChatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
        fakeRepository.messages = listOf(createTestMessage("Test"))

        // When: Invoking use case with specific chat ID
        useCase(specificChatId).asSnapshot() // Trigger the flow to call repository

        // Then: Repository receives correct chat ID
        assertEquals(specificChatId, fakeRepository.lastRequestedChatId)
    }

    @Test
    fun `invoke with large dataset maintains performance`() = runTest {
        // Given: Large dataset (simulating performance scenario)
        val largeMessageSet = (1..1000).map { index ->
            createTestMessage("Performance message $index")
        }
        fakeRepository.messages = largeMessageSet

        // When: Invoking use case (time measurement in test environment)
        val startTime = System.currentTimeMillis()
        val pagingFlow = useCase(testChatId)
        val snapshot = pagingFlow.asSnapshot()
        val endTime = System.currentTimeMillis()

        // Then: Performance should be reasonable and data should be available
        val executionTime = endTime - startTime
        assertTrue(
            executionTime < 100, // Should be fast for in-memory operations
            "Use case took ${executionTime}ms, expected < 100ms",
        )
        assertTrue(snapshot.isNotEmpty(), "Should return data")
    }

    private fun createTestMessage(text: String): Message = DomainTestFixtures.createTestTextMessage(
        sender = DomainTestFixtures.createTestParticipant(
            id = testUserId,
            name = "Test User",
            joinedAt = Instant.fromEpochMilliseconds(1000),
        ),
        text = text,
        createdAt = Instant.fromEpochMilliseconds(1000),
    )

    /**
     * Fake repository for testing GetPagedMessagesUseCase in isolation.
     */
    private class MessageRepositoryFake : MessageRepository {
        var messages: List<Message> = emptyList()
        var lastRequestedChatId: ChatId? = null

        override fun getPagedMessages(chatId: ChatId) = flowOf(PagingData.from(messages)).also {
            lastRequestedChatId = chatId
        }

        // Other MessageRepository methods not needed for these tests
        override suspend fun sendMessage(message: Message) = error("Not implemented")
        override suspend fun editMessage(message: Message) = error("Not implemented")
        override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
            error("Not implemented")
    }
}
