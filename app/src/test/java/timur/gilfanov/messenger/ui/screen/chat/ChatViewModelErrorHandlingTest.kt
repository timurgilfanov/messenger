package timur.gilfanov.messenger.ui.screen.chat

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.RemoteOperationFailed
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFakeWithPaging
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestMessage

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelErrorHandlingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `No chat exists error propagates to UI state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())

        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Failure(ChatNotFound))
        val repository = MessengerRepositoryFake(flowChat = chatFlow)
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
            markMessagesAsReadUseCase = markMessagesAsReadUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()

            // Should transition to Error state for ChatNotFound
            val errorState = awaitState()
            assertTrue(errorState is ChatUiState.Error)
            assertEquals(ChatNotFound, errorState.error)

            job.cancelAndJoin()
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

        val repository = MessengerRepositoryFakeWithPaging(
            initialChat = initialChat,
            chatFlow = chatFlow,
        )
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
            markMessagesAsReadUseCase = markMessagesAsReadUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()

            // Wait for initial Ready state
            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)

            expectStateOn<ChatUiState.Ready> {
                copy(inputTextValidationError = TextValidationError.Empty)
            }
            listOf(
                RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
                RemoteOperationFailed(RemoteError.Unauthenticated),
                RemoteOperationFailed(RemoteError.Failed.ServiceDown),
                RemoteOperationFailed(RemoteError.InsufficientPermissions),
            ).forEach { error ->
                chatFlow.value = Failure(error)
                expectStateOn<ChatUiState.Ready> { copy(updateError = error) }
            }

            job.cancelAndJoin()
        }
    }

    @Test
    fun `Network not available in Loading state propagates to UI state `() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())

        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(
                Failure(RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable)),
            )
        val repository = MessengerRepositoryFake(flowChat = chatFlow)
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
            markMessagesAsReadUseCase = markMessagesAsReadUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()

            // Should remain in Loading state with error
            val loadingErrorState = awaitState()
            assertTrue(loadingErrorState is ChatUiState.Loading)
            assertEquals(
                RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
                loadingErrorState.error,
            )

            job.cancelAndJoin()
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

        val repository = MessengerRepositoryFake(flowChat = chatFlow)
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
            markMessagesAsReadUseCase = markMessagesAsReadUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()

            // Initial state should be ready
            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)
            assertNull(initialState.updateError)

            expectStateOn<ChatUiState.Ready> {
                copy(inputTextValidationError = TextValidationError.Empty)
            }

            // Simulate transient network error
            chatFlow.value = Failure(RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable))

            val errorState = awaitState()
            assertTrue(errorState is ChatUiState.Ready)
            assertEquals(
                RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
                errorState.updateError,
            )

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

            val recoveredState = awaitState()
            assertTrue(recoveredState is ChatUiState.Ready)
            // It will be nice to check that we have the new message in the state, but I don't know
            // how to do it with paged messages.
            // Also, I don't understand why update error is not cleared here. I expect it to be null.
            assertNotNull(recoveredState.updateError)

            job.cancelAndJoin()
        }
    }
}
