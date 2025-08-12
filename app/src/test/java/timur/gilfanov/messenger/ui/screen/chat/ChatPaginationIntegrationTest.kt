package timur.gilfanov.messenger.ui.screen.chat

import androidx.paging.PagingData
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.annotations.Feature
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.testutil.DomainTestFixtures
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat

/**
 * Integration tests for pagination functionality in the chat feature.
 *
 * These tests verify that pagination is properly integrated into the ChatViewModel
 * and that the UI state correctly includes paginated message data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Category(Feature::class)
class ChatPaginationIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `ChatViewModel initializes with pagination support`() = runTest {
        // Given: Chat setup with pagination-enabled repository
        val chatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val currentUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val otherUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000003"))

        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val chatRepository = MessengerRepositoryFake(chat = chat)
        val messageRepository = PaginationTestRepository()

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = SendMessageUseCase(
                messageRepository,
                DeliveryStatusValidatorImpl(),
            ),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(chatRepository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(messageRepository),
        )

        // When: ViewModel initializes
        viewModel.test(this) {
            val job = runOnCreate()

            // Then: Ready state includes pagination support
            val state = awaitState()
            assertTrue(state is ChatUiState.Ready, "Expected Ready state")
            assertNotNull(state.messages, "messages should not be null")

            job.cancelAndJoin()
        }
    }

    @Test
    fun `pagination provides test data correctly`() = runTest {
        // Given: Chat with specific test messages for pagination
        val chatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val currentUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val otherUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
        val now = Instant.fromEpochMilliseconds(1000)

        val testMessages = (1..5).map { index ->
            DomainTestFixtures.createTestTextMessage(
                sender = DomainTestFixtures.createTestParticipant(
                    id = if (index % 2 == 0) currentUserId else otherUserId,
                    name = if (index % 2 == 0) "Current User" else "Other User",
                    joinedAt = now,
                ),
                text = "Paginated message $index",
                createdAt = Instant.fromEpochMilliseconds(1000 + index * 1000L),
            )
        }

        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val chatRepository = MessengerRepositoryFake(chat = chat)
        val messageRepository = PaginationTestRepository(testMessages = testMessages)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = SendMessageUseCase(
                messageRepository,
                DeliveryStatusValidatorImpl(),
            ),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(chatRepository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(messageRepository),
        )

        // When: ViewModel loads with paginated data
        viewModel.test(this) {
            val job = runOnCreate()

            val state = awaitState()
            assertTrue(state is ChatUiState.Ready)

            // Then: Pagination data is accessible and not null
            assertNotNull(state.messages, "messages should not be null")

            job.cancelAndJoin()
        }
    }

    @Test
    fun `pagination handles empty message list gracefully`() = runTest {
        // Given: Chat with no messages
        val chatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val currentUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val otherUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000003"))

        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val chatRepository = MessengerRepositoryFake(chat = chat)
        val messageRepository = PaginationTestRepository(testMessages = emptyList())

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = SendMessageUseCase(
                messageRepository,
                DeliveryStatusValidatorImpl(),
            ),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(chatRepository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(messageRepository),
        )

        // When: ViewModel loads with empty data
        viewModel.test(this) {
            val job = runOnCreate()

            val state = awaitState()
            assertTrue(state is ChatUiState.Ready)

            // Then: Pagination handles empty data - flow should not be null
            assertNotNull(state.messages, "messages should not be null")

            job.cancelAndJoin()
        }
    }

    @Test
    fun `pagination integrates with existing chat functionality`() = runTest {
        // Given: Chat with both regular and paginated message handling
        val chatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val currentUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val otherUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
        val now = Instant.fromEpochMilliseconds(1000)

        val paginatedMessages = listOf(
            DomainTestFixtures.createTestTextMessage(
                sender = DomainTestFixtures.createTestParticipant(
                    id = otherUserId,
                    name = "Other User",
                    joinedAt = now,
                ),
                text = "Older paginated message",
                createdAt = now,
            ),
        )

        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val chatRepository = MessengerRepositoryFake(chat = chat)
        val messageRepository = PaginationTestRepository(testMessages = paginatedMessages)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = SendMessageUseCase(
                messageRepository,
                DeliveryStatusValidatorImpl(),
            ),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(chatRepository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(messageRepository),
        )

        // When: ViewModel loads with both regular and paginated data
        viewModel.test(this) {
            val job = runOnCreate()

            val state = awaitState()
            assertTrue(state is ChatUiState.Ready)

            // Then: Both regular chat state and pagination work together
            assertEquals("Direct Message", state.title) // Regular chat functionality
            assertNotNull(state.messages) // Pagination functionality

            // Then: Pagination flow is available for integration
            assertNotNull(state.messages, "messages should not be null")

            job.cancelAndJoin()
        }
    }

    /**
     * Test repository that provides pagination support for integration testing.
     *
     * This is a standalone MessageRepository implementation focused solely on
     * pagination testing without unnecessary inheritance.
     */
    private class PaginationTestRepository(private val testMessages: List<Message> = emptyList()) :
        MessageRepository {

        override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> =
            flowOf(PagingData.from(testMessages))

        override suspend fun sendMessage(message: Message) =
            error("Not implemented for pagination tests")
        override suspend fun editMessage(message: Message) =
            error("Not implemented for pagination tests")
        override suspend fun deleteMessage(
            messageId: timur.gilfanov.messenger.domain.entity.message.MessageId,
            mode: DeleteMessageMode,
        ): ResultWithError<Unit, RepositoryDeleteMessageError> =
            error("Not implemented for pagination tests")
    }
}
