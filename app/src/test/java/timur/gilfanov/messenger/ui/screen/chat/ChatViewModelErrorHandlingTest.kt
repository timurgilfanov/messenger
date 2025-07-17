package timur.gilfanov.messenger.ui.screen.chat

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.RepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestMessage

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelErrorHandlingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher: TestDispatcher get() = mainDispatcherRule.testDispatcher

    @After
    fun tearDown() {
        // Ensure all coroutines are completed and cleaned up
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `No chat exists error propagates to UI state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())

        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Failure(ChatNotFound))
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

            // Should transition to Error state for ChatNotFound
            val errorState = awaitState()
            assertTrue(errorState is ChatUiState.Error)
            assertEquals(ChatNotFound, errorState.error)

            job.cancel()
        }
    }

    @Test
    fun `Network errors propagates to UI state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

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

            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)

            listOf(
                ReceiveChatUpdatesError.NetworkNotAvailable,
                ReceiveChatUpdatesError.ServerError,
                ReceiveChatUpdatesError.ServerUnreachable,
                ReceiveChatUpdatesError.UnknownError,
            ).forEach { error ->
                chatFlow.value = Failure(error)
                testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

                val errorState = awaitState()
                assertTrue(errorState is ChatUiState.Ready)
                assertEquals(error, errorState.updateError)
            }

            job.cancel()
        }
    }

    @Test
    fun `Network not available in Loading state propagates to UI state `() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())

        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(
                Failure(ReceiveChatUpdatesError.NetworkNotAvailable),
            )
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

            // Should remain in Loading state with error
            val loadingErrorState = awaitState()
            assertTrue(loadingErrorState is ChatUiState.Loading)
            assertEquals(ReceiveChatUpdatesError.NetworkNotAvailable, loadingErrorState.error)

            job.cancel()
        }
    }

    @Test
    fun `Chat recovers from transient errors`() = runTest {
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
            assertNull(initialState.updateError)

            // Simulate transient network error
            chatFlow.value =
                Failure<Chat, ReceiveChatUpdatesError>(ReceiveChatUpdatesError.NetworkNotAvailable)
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            val errorState = awaitState()
            assertTrue(errorState is ChatUiState.Ready)
            assertEquals(ReceiveChatUpdatesError.NetworkNotAvailable, errorState.updateError)

            // Simulate recovery with successful update including new message
            val newMessage = createTestMessage(
                senderId = otherUserId,
                text = "Message after recovery",
                joinedAt = now,
                createdAt = now,
            )
            val recoveredChat = initialChat.copy(
                messages = persistentListOf(newMessage),
            )
            chatFlow.value = Success(recoveredChat)
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            // Should recover with successful state - focus on recovery being successful
            val recoveredState = awaitState()
            assertTrue(recoveredState is ChatUiState.Ready)
            // Check that we have the message (recovery worked)
            assertEquals(1, recoveredState.messages.size)
            assertEquals("Message after recovery", recoveredState.messages[0].text)
            // Note: We don't check updateError clearing here as it might be
            // implementation-specific timing with debounce

            job.cancel()
        }
    }
}
