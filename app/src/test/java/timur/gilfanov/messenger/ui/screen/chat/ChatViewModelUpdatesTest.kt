package timur.gilfanov.messenger.ui.screen.chat

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.annotations.Feature
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.RepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestMessage

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Feature::class)
class ChatViewModelUpdatesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher: TestDispatcher get() = mainDispatcherRule.testDispatcher

    @Test
    fun `message from other participant appears in UI state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val now = Instant.fromEpochMilliseconds(1000)

        val initialChat = createTestChat(chatId, currentUserId, otherUserId)
        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Success(initialChat))

        val repository = RepositoryFake(flowChat = chatFlow)
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            // Initial state should be ready with no messages
            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)
            assertTrue(initialState.messages.isEmpty())

            // Add a new message to the chat
            val newMessage = createTestMessage(
                senderId = otherUserId,
                text = "Hello from other user!",
                joinedAt = now,
                createdAt = now,
            )
            val updatedChat = initialChat.copy(
                messages = persistentListOf(newMessage),
            )

            chatFlow.value = Success(updatedChat)
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            // Verify the new message appears in UI state
            val updatedState = awaitState()
            assertTrue(updatedState is ChatUiState.Ready)
            assertEquals(1, updatedState.messages.size)
            assertEquals("Hello from other user!", updatedState.messages[0].text)
            assertEquals(otherUserId.id.toString(), updatedState.messages[0].senderId)
            assertFalse(updatedState.messages[0].isFromCurrentUser)

            job.cancel()
        }
    }

    @Test
    fun `chat metadata updates seen in UI state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val newParticipantId = ParticipantId(UUID.randomUUID())
        val now = Instant.fromEpochMilliseconds(1000)

        val initialChat = createTestChat(chatId, currentUserId, otherUserId, isOneToOne = false)
        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Success(initialChat))

        val repository = RepositoryFake(flowChat = chatFlow)
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            // Initial state should be ready with group chat status
            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)
            assertEquals("Group Chat", initialState.title)
            assertEquals(2, initialState.participants.size)
            assertTrue(initialState.isGroupChat)
            assertEquals(ChatStatus.Group(2), initialState.status)

            // Update chat metadata: change name and add new participant
            val newParticipant = buildParticipant {
                id = newParticipantId
                name = "New Participant"
                joinedAt = now
            }
            val updatedChat = initialChat.copy(
                name = "Updated Group Chat",
                participants = persistentSetOf(
                    *initialChat.participants.toTypedArray(),
                    newParticipant,
                ),
            )

            chatFlow.value = Success(updatedChat)
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            // Verify metadata changes are reflected in UI state
            val updatedState = awaitState()
            assertTrue(updatedState is ChatUiState.Ready)
            assertEquals("Updated Group Chat", updatedState.title)
            assertEquals(3, updatedState.participants.size)
            assertTrue(updatedState.isGroupChat)
            assertEquals(ChatStatus.Group(3), updatedState.status)

            // Verify new participant appears in participants list
            val newParticipantUi = updatedState.participants.find { it.id == newParticipantId }
            assertEquals("New Participant", newParticipantUi?.name)

            job.cancel()
        }
    }

    @Test
    fun `few rapid messages result in one chat update`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val now = Instant.fromEpochMilliseconds(1000)

        val initialChat = createTestChat(chatId, currentUserId, otherUserId)
        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Success(initialChat))

        val repository = RepositoryFake(flowChat = chatFlow)
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            // Initial state should be ready
            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)
            assertEquals("Direct Message", initialState.title)

            // Send rapid updates within debounce window (200ms)
            val message1 =
                createTestMessage(otherUserId, "Message 1", joinedAt = now, createdAt = now)
            val message2 =
                createTestMessage(otherUserId, "Message 2", joinedAt = now, createdAt = now)
            val message3 =
                createTestMessage(otherUserId, "Final Message", joinedAt = now, createdAt = now)

            // Emit updates rapidly
            chatFlow.value =
                Success(
                    initialChat.copy(messages = persistentListOf(message1)),
                )
            testDispatcher.scheduler.advanceTimeBy(50L) // Less than debounce delay

            chatFlow.value = Success(
                initialChat.copy(messages = persistentListOf(message1, message2)),
            )
            testDispatcher.scheduler.advanceTimeBy(50L) // Less than debounce delay

            chatFlow.value = Success(
                initialChat.copy(messages = persistentListOf(message1, message2, message3)),
            )
            testDispatcher.scheduler.advanceTimeBy(200L) // Complete debounce delay

            // Only the final update should be emitted due to debounce
            val finalState = awaitState()
            assertTrue(finalState is ChatUiState.Ready)
            assertEquals(3, finalState.messages.size)
            assertEquals("Final Message", finalState.messages[2].text)

            job.cancel()
        }
    }
}
