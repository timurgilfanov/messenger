package timur.gilfanov.messenger.ui.screen.chat

import java.util.UUID
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFakeWithPaging
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestMessage

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelUpdatesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher: TestDispatcher get() = mainDispatcherRule.testDispatcher

    @Test
    @Ignore("Receiving messages from PagingData not implemented yet")
    fun `message from other participant appears in UI state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val now = Instant.fromEpochMilliseconds(1000)

        val initialChat = createTestChat(chatId, currentUserId, otherUserId)
        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Success(initialChat))

        val repository = MessengerRepositoryFakeWithPaging(
            initialChat = initialChat,
            chatFlow = chatFlow,
        )
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()

            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)

            expectStateOn<ChatUiState.Ready> {
                copy(inputTextValidationError = TextValidationError.Empty)
            }

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

            // Verify the new message appears in UI state
            val updatedState = awaitState()
            assertTrue(updatedState is ChatUiState.Ready)
//            assertEquals(1, messages.size)
//            val message = messages[0]
//            assertIs<TextMessage>(message)
//            assertEquals("Hello from other user!", message.text)
//            assertEquals(otherUserId.id.toString(), message.sender.id.toString())

            job.cancelAndJoin()
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

        val repository = MessengerRepositoryFake(flowChat = chatFlow)
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()

            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)
            assertEquals("Group Chat", initialState.title)
            assertEquals(2, initialState.participants.size)
            assertTrue(initialState.isGroupChat)
            assertEquals(ChatStatus.Group(2), initialState.status)

            expectStateOn<ChatUiState.Ready> {
                copy(inputTextValidationError = TextValidationError.Empty)
            }

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

            job.cancelAndJoin()
        }
    }

    @Ignore("Receiving messages from PagingData not implemented yet")
    @Test
    fun `few rapid messages result in one chat update`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val now = Instant.fromEpochMilliseconds(1000)

        val initialChat = createTestChat(chatId, currentUserId, otherUserId)
        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Success(initialChat))

        val repository = MessengerRepositoryFakeWithPaging(
            initialChat = initialChat,
            chatFlow = chatFlow,
        )
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()

            // Initial state should be ready
            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)
            assertEquals("Direct Message", initialState.title)

            expectStateOn<ChatUiState.Ready> {
                copy(inputTextValidationError = TextValidationError.Empty)
            }

            // Send rapid updates within debounce window (200ms)
            val message1 =
                createTestMessage(otherUserId, "Message 1", joinedAt = now, createdAt = now)
            val message2 =
                createTestMessage(otherUserId, "Message 2", joinedAt = now, createdAt = now)
            val message3 =
                createTestMessage(otherUserId, "Final Message", joinedAt = now, createdAt = now)

            // Emit updates rapidly
            chatFlow.value = Success(
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
//            assertEquals(3, finalState.messages.size)
//            assertEquals("Final Message", finalState.messages[2].text)

            job.cancelAndJoin()
        }
    }
}
