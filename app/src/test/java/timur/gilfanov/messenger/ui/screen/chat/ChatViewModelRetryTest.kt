package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.domain.usecase.message.repository.SendMessageRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
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

    private fun progress(
        sender: Participant,
        status: DeliveryStatus,
        id: MessageId = FAILED_MESSAGE_ID,
    ): TextMessage = buildTextMessage {
        this.id = id
        this.sender = sender
        recipient = TEST_CHAT_ID
        createdAt = TEST_INSTANT
        text = TEXT
        deliveryStatus = status
    }

    private fun <R> createViewModel(
        repository: R,
    ): ChatViewModel
        where R : ChatRepository, R : MessageRepository = ChatViewModel(
        chatIdUuid = TEST_CHAT_ID.id,
        savedStateHandle = SavedStateHandle(),
        sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
        receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
        getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
        markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
    )

    private fun MessengerRepositoryFakeWithTimeline.timelineMessage(): TextMessage =
        assertIs<TextMessage>(timeline.single())

    /**
     * Asserts on the message actually handed to the send use case (captured by the fake),
     * not the scripted timeline echo — so an id-reuse regression cannot hide behind the
     * hardcoded [progress] id.
     */
    private fun MessengerRepositoryFakeWithTimeline.assertRetriedMessageIsFailedOne() {
        val sent = assertIs<TextMessage>(sentMessages.first())
        assertEquals(FAILED_MESSAGE_ID, sent.id)
        assertEquals(TEXT, sent.text)
        assertNull(sent.deliveryStatus)
        assertEquals(TEST_INSTANT, sent.createdAt)
    }

    @Test
    fun `retry re-sends same id and text, clears status, and reaches Sent`() = runTest {
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

        val laterNow = TEST_INSTANT + 10.seconds
        viewModel.retryMessage(FAILED_MESSAGE_ID, laterNow)
        advanceUntilIdle()

        assertEquals(1, repository.sentMessages.size)
        val retried = assertIs<TextMessage>(repository.sentMessages.single())
        assertEquals(FAILED_MESSAGE_ID, retried.id)
        assertEquals(TEXT, retried.text)
        assertNull(retried.deliveryStatus)
        assertEquals(TEST_INSTANT, retried.createdAt)

        val state = viewModel.state.value
        assertTrue(state is ChatUiState.Ready)
        assertNull(state.dialogError)
        assertEquals(false, state.isSending)
        assertTrue(effects.isEmpty())
        assertEquals(DeliveryStatus.Sent, repository.timelineMessage().deliveryStatus)
    }

    @Test
    fun `retry local failure shows dialog and message stays failed and retryable`() = runTest {
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
        repository.assertRetriedMessageIsFailedOne()
        val afterFailure = viewModel.state.value
        assertTrue(afterFailure is ChatUiState.Ready)
        val dialogError = assertIs<ReadyError.SendMessageError>(afterFailure.dialogError)
        assertIs<SendMessageError.LocalOperationFailed>(dialogError.error)
        assertIs<DeliveryStatus.Failed>(repository.timelineMessage().deliveryStatus)

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()
        assertEquals(2, repository.sentMessages.size)
    }

    @Test
    fun `retry remote failure stays silent and message stays failed and retryable`() = runTest {
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
        repository.assertRetriedMessageIsFailedOne()
        val afterFailure = viewModel.state.value
        assertTrue(afterFailure is ChatUiState.Ready)
        assertNull(afterFailure.dialogError)
        assertIs<DeliveryStatus.Failed>(repository.timelineMessage().deliveryStatus)

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()
        assertEquals(2, repository.sentMessages.size)
    }

    @Test
    fun `retry succeeds under debounce rule despite its own failed timeline entry`() = runTest {
        val (baseChat, currentUser) =
            chatWith(DeliveryStatus.Failed(DeliveryError.NetworkUnavailable))
        val chat = baseChat.copy(
            rules = persistentSetOf(CreateMessageRule.Debounce(5.seconds)),
        )
        val retryFlow: Flow<ResultWithError<Message, SendMessageRepositoryError>> = flowOf(
            ResultWithError.Success(progress(currentUser, Sending(0))),
            ResultWithError.Success(progress(currentUser, DeliveryStatus.Sent)),
        )
        val repository = MessengerRepositoryFakeWithTimeline(chat, listOf(retryFlow))
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        val withinDebounceWindow = TEST_INSTANT + 1.seconds
        viewModel.retryMessage(FAILED_MESSAGE_ID, withinDebounceWindow)
        advanceUntilIdle()

        assertEquals(1, repository.sentMessages.size)
        repository.assertRetriedMessageIsFailedOne()
        val state = viewModel.state.value
        assertTrue(state is ChatUiState.Ready)
        assertNull(state.dialogError)
        assertEquals(DeliveryStatus.Sent, repository.timelineMessage().deliveryStatus)
    }

    @Test
    fun `concurrent retry of the same message launches only one send`() = runTest {
        val (chat, currentUser) = chatWith(DeliveryStatus.Failed(DeliveryError.NetworkUnavailable))
        val gate = Channel<ResultWithError<Message, SendMessageRepositoryError>>(Channel.UNLIMITED)
        val repository = MessengerRepositoryFakeWithTimeline(chat, listOf(gate.receiveAsFlow()))
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()

        assertEquals(1, repository.sentMessages.size)
        repository.assertRetriedMessageIsFailedOne()

        gate.send(ResultWithError.Success(progress(currentUser, Sending(0))))
        gate.send(ResultWithError.Success(progress(currentUser, DeliveryStatus.Sent)))
        gate.close()
        advanceUntilIdle()

        assertEquals(1, repository.sentMessages.size)
        val state = viewModel.state.value
        assertTrue(state is ChatUiState.Ready)
        assertNull(state.dialogError)
        assertEquals(DeliveryStatus.Sent, repository.timelineMessage().deliveryStatus)
    }

    @Test
    fun `concurrent retry of distinct messages launches a send for each`() = runTest {
        val baseChat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
        val currentUser = baseChat.participants.first { it.isCurrentUser }
        val secondId = MessageId(UUID.fromString("00000000-0000-0000-0000-000000000005"))
        fun failed(id: MessageId) = buildTextMessage {
            this.id = id
            sender = currentUser
            recipient = TEST_CHAT_ID
            createdAt = TEST_INSTANT
            text = TEXT
            deliveryStatus = DeliveryStatus.Failed(DeliveryError.NetworkUnavailable)
        }
        val chat = baseChat.copy(
            messages = persistentListOf(failed(FAILED_MESSAGE_ID), failed(secondId)),
        )
        val gateA = Channel<ResultWithError<Message, SendMessageRepositoryError>>(Channel.UNLIMITED)
        val gateB = Channel<ResultWithError<Message, SendMessageRepositoryError>>(Channel.UNLIMITED)
        val repository = MessengerRepositoryFakeWithTimeline(
            chat,
            listOf(gateA.receiveAsFlow(), gateB.receiveAsFlow()),
        )
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        viewModel.retryMessage(secondId, TEST_INSTANT)
        advanceUntilIdle()

        assertEquals(2, repository.sentMessages.size)
        assertEquals(
            setOf(FAILED_MESSAGE_ID, secondId),
            repository.sentMessages.map { it.id }.toSet(),
        )

        gateA.send(ResultWithError.Success(progress(currentUser, Sending(0))))
        gateA.send(ResultWithError.Success(progress(currentUser, DeliveryStatus.Sent)))
        gateB.send(ResultWithError.Success(progress(currentUser, Sending(0), secondId)))
        gateB.send(ResultWithError.Success(progress(currentUser, DeliveryStatus.Sent, secondId)))
        gateA.close()
        gateB.close()
        advanceUntilIdle()

        assertEquals(2, repository.sentMessages.size)
        val state = viewModel.state.value
        assertTrue(state is ChatUiState.Ready)
        assertNull(state.dialogError)
        val terminalStatuses = repository.timeline
            .filterIsInstance<TextMessage>()
            .associate { it.id to it.deliveryStatus }
        assertEquals(DeliveryStatus.Sent, terminalStatuses[FAILED_MESSAGE_ID])
        assertEquals(DeliveryStatus.Sent, terminalStatuses[secondId])
    }

    @Test
    fun `retry succeeds under CanNotWriteAfterJoining rule`() = runTest {
        val (baseChat, currentUser) =
            chatWith(DeliveryStatus.Failed(DeliveryError.NetworkUnavailable))
        val chat = baseChat.copy(
            rules = persistentSetOf(CreateMessageRule.CanNotWriteAfterJoining(5.seconds)),
        )
        val retryFlow: Flow<ResultWithError<Message, SendMessageRepositoryError>> = flowOf(
            ResultWithError.Success(progress(currentUser, Sending(0))),
            ResultWithError.Success(progress(currentUser, DeliveryStatus.Sent)),
        )
        val repository = MessengerRepositoryFakeWithTimeline(chat, listOf(retryFlow))
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        val afterJoinWindow = currentUser.joinedAt + 10.seconds
        viewModel.retryMessage(FAILED_MESSAGE_ID, afterJoinWindow)
        advanceUntilIdle()

        assertEquals(1, repository.sentMessages.size)
        repository.assertRetriedMessageIsFailedOne()
        val state = viewModel.state.value
        assertTrue(state is ChatUiState.Ready)
        assertNull(state.dialogError)
        assertEquals(DeliveryStatus.Sent, repository.timelineMessage().deliveryStatus)
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

    @Test
    fun `retry is a no-op for an in-flight Sending message`() = runTest {
        val (chat, _) = chatWith(Sending(0))
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

    @Test
    fun `retry is a no-op when chat is not ready`() = runTest {
        val repository = MessengerRepositoryFake(flowChat = MutableSharedFlow())
        val viewModel = createViewModel(repository)

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.retryMessage(FAILED_MESSAGE_ID, TEST_INSTANT)
        advanceUntilIdle()

        assertTrue(repository.sentMessages.isEmpty())
        assertTrue(viewModel.state.value is ChatUiState.Loading)
    }
}
