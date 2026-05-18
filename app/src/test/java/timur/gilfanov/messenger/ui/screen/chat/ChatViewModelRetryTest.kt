package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.domain.usecase.message.repository.SendMessageRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFakeWithTimeline
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_CHAT_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_CURRENT_USER_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_OTHER_USER_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelRetryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    companion object {
        private val FAILED_MESSAGE_ID =
            MessageId(UUID.fromString("00000000-0000-0000-0000-000000000004"))
        private val UNKNOWN_MESSAGE_ID =
            MessageId(UUID.fromString("00000000-0000-0000-0000-000000000099"))
        private val TEST_INSTANT = Instant.fromEpochMilliseconds(1000)
        private const val TEXT = "Test message"
    }

    private fun chatWith(status: DeliveryStatus): Pair<Chat, Participant> {
        val baseChat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
        val currentUser = baseChat.participants.first { it.isCurrentUser }
        val message = buildTextMessage {
            id = FAILED_MESSAGE_ID
            sender = currentUser
            recipient = TEST_CHAT_ID
            createdAt = TEST_INSTANT
            text = TEXT
            deliveryStatus = status
        }
        return baseChat.copy(messages = persistentListOf(message)) to currentUser
    }

    private fun progress(sender: Participant, status: DeliveryStatus): TextMessage =
        buildTextMessage {
            id = FAILED_MESSAGE_ID
            this.sender = sender
            recipient = TEST_CHAT_ID
            createdAt = TEST_INSTANT
            text = TEXT
            deliveryStatus = status
        }

    private fun createViewModel(repository: MessengerRepositoryFakeWithTimeline): ChatViewModel =
        ChatViewModel(
            chatIdUuid = TEST_CHAT_ID.id,
            savedStateHandle = SavedStateHandle(),
            sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
        )

    @Test
    fun `retry re-sends same id and text with cleared status and no dialog`() = runTest {
        val (chat, currentUser) = chatWith(DeliveryStatus.Failed(DeliveryError.NetworkUnavailable))
        val retryFlow: Flow<ResultWithError<Message, SendMessageRepositoryError>> = flowOf(
            ResultWithError.Success(progress(currentUser, Sending(0))),
            ResultWithError.Success(progress(currentUser, DeliveryStatus.Sent)),
        )
        val repository = MessengerRepositoryFakeWithTimeline(chat, listOf(retryFlow))
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        val effects = mutableListOf<ChatSideEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        advanceUntilIdle()

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()

        assertEquals(1, repository.sentMessages.size)
        val retried = assertIs<TextMessage>(repository.sentMessages.single())
        assertEquals(FAILED_MESSAGE_ID, retried.id)
        assertEquals(TEXT, retried.text)
        assertNull(retried.deliveryStatus)

        val state = viewModel.state.value
        assertTrue(state is ChatUiState.Ready)
        assertNull(state.dialogError)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `retry local failure shows dialog and message stays retryable`() = runTest {
        val (chat, currentUser) = chatWith(DeliveryStatus.Failed(DeliveryError.NetworkUnavailable))
        val failingRetry: Flow<ResultWithError<Message, SendMessageRepositoryError>> = flowOf(
            ResultWithError.Success(progress(currentUser, Sending(0))),
            ResultWithError.Failure(
                SendMessageRepositoryError.LocalOperationFailed(
                    LocalStorageError.TemporarilyUnavailable,
                ),
            ),
        )
        val repository = MessengerRepositoryFakeWithTimeline(chat, listOf(failingRetry))
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()

        assertEquals(1, repository.sentMessages.size)
        val afterFailure = viewModel.state.value
        assertTrue(afterFailure is ChatUiState.Ready)
        val dialogError = assertIs<ReadyError.SendMessageError>(afterFailure.dialogError)
        assertIs<SendMessageError.LocalOperationFailed>(dialogError.error)

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()
        assertEquals(2, repository.sentMessages.size)
    }

    @Test
    fun `retry remote failure stays silent and message stays retryable`() = runTest {
        val (chat, currentUser) = chatWith(DeliveryStatus.Failed(DeliveryError.NetworkUnavailable))
        val failingRetry: Flow<ResultWithError<Message, SendMessageRepositoryError>> = flowOf(
            ResultWithError.Success(progress(currentUser, Sending(0))),
            ResultWithError.Failure(
                SendMessageRepositoryError.RemoteOperationFailed(
                    RemoteError.Failed.NetworkNotAvailable,
                ),
            ),
        )
        val repository = MessengerRepositoryFakeWithTimeline(chat, listOf(failingRetry))
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()

        assertEquals(1, repository.sentMessages.size)
        val afterFailure = viewModel.state.value
        assertTrue(afterFailure is ChatUiState.Ready)
        assertNull(afterFailure.dialogError)

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()
        assertEquals(2, repository.sentMessages.size)
    }

    @Test
    fun `retry is a no-op for unknown message id`() = runTest {
        val (chat, _) = chatWith(DeliveryStatus.Failed(DeliveryError.NetworkUnavailable))
        val repository = MessengerRepositoryFakeWithTimeline(chat, emptyList())
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.retryMessage(UNKNOWN_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()

        assertTrue(repository.sentMessages.isEmpty())
        val state = viewModel.state.value
        assertTrue(state is ChatUiState.Ready)
        assertNull(state.dialogError)
    }

    @Test
    fun `retry is a no-op for a non-failed message`() = runTest {
        val (chat, _) = chatWith(DeliveryStatus.Sent)
        val repository = MessengerRepositoryFakeWithTimeline(chat, emptyList())
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()

        assertTrue(repository.sentMessages.isEmpty())
        val state = viewModel.state.value
        assertTrue(state is ChatUiState.Ready)
        assertNull(state.dialogError)
    }
}
