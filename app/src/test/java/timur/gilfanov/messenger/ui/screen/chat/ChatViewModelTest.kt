package timur.gilfanov.messenger.ui.screen.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.annotations.Feature
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeliveryError.NetworkUnavailable
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Delivered
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Failed
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Read
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageError
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageUseCase

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Feature::class)
class ChatViewModelTest {

    private class RepositoryFake(
        private val chat: Chat? = null,
        private val flowChat: Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>? = null,
        private val flowSendMessage: Flow<Message>? = null,
    ) : ParticipantRepository by ParticipantRepositoryNotImplemented() {

        override suspend fun sendMessage(message: Message): Flow<Message> =
            flowSendMessage ?: flowOf(
                when (message) {
                    is TextMessage -> message.copy(deliveryStatus = Sending(0))
                    else -> message
                },
            )

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = flowChat ?: flowOf(
            chat?.let { Success(it) } ?: Failure(ChatNotFound),
        )
    }

    private class RepositoryFake2(chat: Chat, val statuses: List<DeliveryStatus>) :
        ParticipantRepository by ParticipantRepositoryNotImplemented() {

        private val chatFlow = MutableStateFlow(chat)

        override suspend fun sendMessage(message: Message): Flow<Message> = flowOf(
            *(statuses.map { (message as TextMessage).copy(deliveryStatus = it) }.toTypedArray()),
        ).onEach { msg ->
            delay(10) // to pass immediate state updates, like text input
            chatFlow.update { currentChat ->
                val messages = currentChat.messages.toMutableList().apply {
                    val indexOfFirst = indexOfFirst { it.id == msg.id }
                    if (indexOfFirst != -1) {
                        this[indexOfFirst] = msg
                    } else {
                        add(msg)
                    }
                }.toImmutableList()
                currentChat.copy(messages = messages)
            }
            yield() // helps to update chat flow immediately somehow. todo why?
        }

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = chatFlow.map { chat ->
            Success<Chat, ReceiveChatUpdatesError>(chat)
        }
    }

    private fun createTestChat(
        chatId: ChatId = ChatId(UUID.randomUUID()),
        currentUserId: ParticipantId = ParticipantId(UUID.randomUUID()),
        otherUserId: ParticipantId = ParticipantId(UUID.randomUUID()),
        messages: List<Message> = emptyList(),
        isOneToOne: Boolean = true,
    ): Chat {
        val currentUser = buildParticipant {
            id = currentUserId
            name = "Current User"
            joinedAt = Instant.fromEpochMilliseconds(1000)
        }

        val otherUser = buildParticipant {
            id = otherUserId
            name = "Other User"
            joinedAt = Instant.fromEpochMilliseconds(1000)
        }

        return buildChat {
            id = chatId
            name = if (isOneToOne) "Direct Message" else "Group Chat"
            participants = persistentSetOf(currentUser, otherUser)
            this.messages = persistentListOf<Message>().addAll(messages)
            this.isOneToOne = isOneToOne
        }
    }

    private fun createTestMessage(
        senderId: ParticipantId,
        text: String = "Test message",
        deliveryStatus: DeliveryStatus = DeliveryStatus.Sent,
        joinedAt: Instant,
        createdAt: Instant,
    ): TextMessage {
        val sender = buildParticipant {
            id = senderId
            name = "Current User"
            this.joinedAt = joinedAt
        }

        return buildTextMessage {
            this.sender = sender
            this.text = text
            this.deliveryStatus = deliveryStatus
            this.createdAt = createdAt
        }
    }

    @Test
    fun `loads chat successfully`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val now = Instant.fromEpochMilliseconds(1000)
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val createdAtUi = formatter.format(Date(1000))
        val message = createTestMessage(currentUserId, "Hello!", joinedAt = now, createdAt = now)
        val chat = createTestChat(chatId, currentUserId, otherUserId, listOf(message))

        val repository = RepositoryFake(
            chat = chat,
            flowChat = flowOf(Success(chat)),
        )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.test(this) {
            runOnCreate()
            expectState {
                ChatUiState.Ready(
                    id = chatId,
                    title = "Direct Message",
                    participants = persistentListOf(
                        ParticipantUiModel(
                            id = currentUserId,
                            name = "Current User",
                            pictureUrl = null,
                        ),
                        ParticipantUiModel(
                            id = otherUserId,
                            name = "Other User",
                            pictureUrl = null,
                        ),
                    ),
                    isGroupChat = false,
                    messages = persistentListOf(
                        MessageUiModel(
                            id = message.id.id.toString(),
                            text = "Hello!",
                            senderId = currentUserId.id.toString(),
                            senderName = "Current User",
                            createdAt = createdAtUi,
                            deliveryStatus = DeliveryStatus.Sent,
                            isFromCurrentUser = true,
                        ),
                    ),
                    inputText = "",
                    isSending = false,
                    status = ChatStatus.OneToOne(null),
                )
            }
        }
    }

    @Test
    fun `loads group chat successfully`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val chat = createTestChat(chatId, currentUserId, otherUserId, isOneToOne = false)

        val repository = RepositoryFake(
            chat = chat,
            flowChat = flowOf(Success(chat)),
        )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.test(this) {
            runOnCreate()
            expectState {
                ChatUiState.Ready(
                    id = chatId,
                    title = "Group Chat",
                    participants = persistentListOf(
                        ParticipantUiModel(
                            id = currentUserId,
                            name = "Current User",
                            pictureUrl = null,
                        ),
                        ParticipantUiModel(
                            id = otherUserId,
                            name = "Other User",
                            pictureUrl = null,
                        ),
                    ),
                    isGroupChat = true,
                    messages = persistentListOf(),
                    inputText = "",
                    isSending = false,
                    status = ChatStatus.Group(2),
                )
            }
        }
    }

    @Test
    fun `handles chat loading error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())

        val repository = RepositoryFake(
            flowChat = flowOf(Failure(NetworkNotAvailable)),
        )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.test(this) {
            runOnCreate()
            expectState {
                ChatUiState.Loading(NetworkNotAvailable)
            }
        }
    }

    @Test
    fun `updateInputText updates state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val chat = createTestChat(chatId, currentUserId)

        val repository = RepositoryFake(
            chat = chat,
            flowChat = flowOf(Success(chat)),
        )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.test(this) {
            runOnCreate()
            val readyState = awaitState()
            assertTrue(
                readyState is ChatUiState.Ready,
                "Expected Ready state, but got: $readyState",
            )
            assertNull(readyState.dialogError)

            viewModel.updateInputText("Hello world")
            expectStateOn<ChatUiState.Ready> {
                copy(inputText = "Hello world")
            }
        }
    }

    @Test
    fun `sending a message clears input only once`() = runTest {
        listOf(
            listOf(Sending(0), Sending(50)),
            listOf(Sending(0), Failed(NetworkUnavailable)),
            listOf(Sending(0), Delivered),
            listOf(Sending(0), Read),
        ).forEach { statuses ->
            val chatId = ChatId(UUID.randomUUID())
            val currentUserId = ParticipantId(UUID.randomUUID())
            val chat = createTestChat(chatId, currentUserId)
            val now = Instant.fromEpochMilliseconds(1000)
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            val createdAt = formatter.format(Date(1000))
            val message = buildTextMessage {
                sender = chat.participants.first { it.id == currentUserId }
                recipient = chatId
                this.createdAt = now
                text = "Test message"
            }
            val rep = RepositoryFake2(chat, statuses)
            val viewModel = ChatViewModel(
                chatIdUuid = chatId.id,
                currentUserIdUuid = currentUserId.id,
                sendMessageUseCase = SendMessageUseCase(rep, DeliveryStatusValidatorImpl()),
                receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(rep),
            )
            viewModel.test(this) {
                val job = runOnCreate()
                awaitState().let { state ->
                    assertTrue(state is ChatUiState.Ready, "Expected Ready state, but got: $state")
                    assertEquals("", state.inputText)
                    assertFalse(state.isSending)
                }

                viewModel.updateInputText("Test message")
                expectStateOn<ChatUiState.Ready> { copy(inputText = "Test message") }

                viewModel.sendMessage(message.id, now = now)
                expectStateOn<ChatUiState.Ready> { copy(isSending = true) }

                val messageUi = MessageUiModel(
                    id = message.id.id.toString(),
                    text = "Test message",
                    senderId = currentUserId.id.toString(),
                    senderName = "Current User",
                    createdAt = createdAt,
                    deliveryStatus = statuses[0],
                    isFromCurrentUser = true,
                )
                expectStateOn<ChatUiState.Ready> { copy(messages = persistentListOf(messageUi)) }
                expectStateOn<ChatUiState.Ready> {
                    copy(isSending = false, inputText = "", messages = persistentListOf(messageUi))
                }
                viewModel.updateInputText("Test message 2")
                expectStateOn<ChatUiState.Ready> { copy(inputText = "Test message 2") }
                expectStateOn<ChatUiState.Ready> {
                    copy(messages = persistentListOf(messageUi.copy(deliveryStatus = statuses[1])))
                }
                job.cancel() // Need this because repo chatFlow is infinite and will never complete
            }
        }
    }

    @Test
    fun `dismissDialogError clears error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())

        val chat = createTestChat(chatId, currentUserId)
        val repository = RepositoryFake(chat = chat)

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.test(this) {
            runOnCreate()
            val readyState = awaitState()
            assertTrue(readyState is ChatUiState.Ready)
            assertNull(readyState.dialogError)

            // Sending a empty text message should cause an error
            viewModel.sendMessage(MessageId(UUID.randomUUID()), Instant.fromEpochMilliseconds(1000))

            val sendingState = awaitState()
            assertTrue(sendingState is ChatUiState.Ready)
            assertTrue(sendingState.isSending)
            assertNull(sendingState.dialogError)

            expectStateOn<ChatUiState.Ready> {
                copy(
                    isSending = false,
                    dialogError = ReadyError.SendMessageError(
                        error = SendMessageError.MessageIsNotValid(
                            reason = TextValidationError.Empty,
                        ),
                    ),
                )
            }

            viewModel.dismissDialogError()

            expectStateOn<ChatUiState.Ready> {
                copy(dialogError = null)
            }
        }
    }
}
