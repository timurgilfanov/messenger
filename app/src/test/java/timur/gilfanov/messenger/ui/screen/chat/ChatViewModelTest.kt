package timur.gilfanov.messenger.ui.screen.chat

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.Feature
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageUseCase

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Feature::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class RepositoryFake(
        private val chat: Chat? = null,
        private val flowChat: Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>? = null,
        private val flowSendMessage: Flow<Message>? = null,
    ) : ParticipantRepository by ParticipantRepositoryNotImplemented() {

        override suspend fun sendMessage(message: Message): Flow<Message> =
            flowSendMessage ?: flowOf(
                when (message) {
                    is TextMessage -> message.copy(deliveryStatus = DeliveryStatus.Sending(0))
                    else -> message
                },
            )

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = flowChat ?: flowOf(
            chat?.let { Success(it) } ?: Failure(ChatNotFound),
        )
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
    ): TextMessage {
        val sender = buildParticipant {
            id = senderId
            name = "Test User"
            joinedAt = Instant.fromEpochMilliseconds(1000)
        }

        return buildTextMessage {
            this.sender = sender
            this.text = text
            this.deliveryStatus = deliveryStatus
            this.createdAt = Instant.fromEpochMilliseconds(1000)
        }
    }

    @Test
    fun `initial state is loading`() = runTest {
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

        viewModel.uiState.test {
            // First emission should be the loading state
            val loadingState = awaitItem()
            assertTrue(loadingState is ChatUiState.Loading)
            assertNull(loadingState.error)

            // Second emission should be the ready state
            val readyState = awaitItem()
            assertTrue(readyState is ChatUiState.Ready)
            assertNotNull(readyState)
            assertNull(readyState.dialogError)
        }
    }

    @Test
    fun `loads chat successfully`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val message = createTestMessage(currentUserId, "Hello!")
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

        viewModel.uiState.test {
            // Skip loading state
            awaitItem()

            val readyState = awaitItem() as ChatUiState.Ready
            assertEquals(1, readyState.messages.size)
            assertEquals("Hello!", readyState.messages[0].text)
            assertTrue(readyState.messages[0].isFromCurrentUser)
            assertEquals("Direct Message", readyState.title)
            assertEquals(2, readyState.participants.size)
            assertTrue(readyState.status is ChatStatus.OneToOne)
            assertNull(readyState.dialogError)
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

        viewModel.uiState.test {
            // Skip loading state
            awaitItem()

            val readyState = awaitItem() as ChatUiState.Ready
            assertTrue(readyState.status is ChatStatus.Group)
            assertEquals(2, readyState.status.participantCount)
            assertTrue(readyState.isGroupChat)
            assertNull(readyState.dialogError)
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

        viewModel.uiState.test {
            // First emission should be loading
            awaitItem()

            // Second emission should be loading with error
            val loadingWithError = awaitItem() as ChatUiState.Loading
            assertEquals(NetworkNotAvailable, loadingWithError.error)
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

        viewModel.uiState.test {
            val loadingState = awaitItem()
            assertTrue(loadingState is ChatUiState.Loading)

            val readyState = awaitItem()
            assertTrue(readyState is ChatUiState.Ready)

            viewModel.updateInputText("Hello world")

            val updatedState = awaitItem() as ChatUiState.Ready
            assertEquals("Hello world", updatedState.inputText)
            assertNull(updatedState.dialogError)
        }
    }

    @Test
    fun `sendMessage clears input and sets sending state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val chat = createTestChat(chatId, currentUserId, otherUserId)

        val message = buildTextMessage {
            sender = chat.participants.first { it.id == currentUserId }
            recipient = chatId
            createdAt = Instant.fromEpochMilliseconds(1000)
            text = "Test message"
        }
        val repository =
            RepositoryFake(
                chat = chat,
                flowSendMessage = flowOf(
                    message.copy(deliveryStatus = DeliveryStatus.Sending(0)),
                    message.copy(deliveryStatus = DeliveryStatus.Sent),
                ),
            )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.uiState.test {
            val loadingState = awaitItem()
            assertTrue(loadingState is ChatUiState.Loading)

            val readyState = awaitItem()
            assertTrue(readyState is ChatUiState.Ready)

            viewModel.updateInputText("Test message")
            val stateWithInput = awaitItem() as ChatUiState.Ready
            assertEquals("Test message", stateWithInput.inputText)

            viewModel.sendMessage()

            val sendingState = awaitItem() as ChatUiState.Ready
            assertTrue(sendingState.isSending)
            assertEquals("Test message", stateWithInput.inputText)

            val sentState = awaitItem() as ChatUiState.Ready
            assertFalse(sentState.isSending)
            assertEquals("", sentState.inputText)
        }
    }

    @Test
    fun `sendMessage clears input and sets delivered state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val chat = createTestChat(chatId, currentUserId, otherUserId)

        val message = buildTextMessage {
            sender = chat.participants.first { it.id == currentUserId }
            recipient = chatId
            createdAt = Instant.fromEpochMilliseconds(1000)
            text = "Test message"
        }
        val repository =
            RepositoryFake(
                chat = chat,
                flowSendMessage = flowOf(
                    message.copy(deliveryStatus = DeliveryStatus.Sending(0)),
                    message.copy(deliveryStatus = DeliveryStatus.Delivered),
                ),
            )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.uiState.test {
            val loadingState = awaitItem()
            assertTrue(loadingState is ChatUiState.Loading)

            val readyState = awaitItem()
            assertTrue(readyState is ChatUiState.Ready)

            viewModel.updateInputText("Test message")
            val stateWithInput = awaitItem() as ChatUiState.Ready
            assertEquals("Test message", stateWithInput.inputText)

            viewModel.sendMessage()

            val sendingState = awaitItem() as ChatUiState.Ready
            assertTrue(sendingState.isSending)
            assertEquals("Test message", stateWithInput.inputText)

            val sentState = awaitItem() as ChatUiState.Ready
            assertFalse(sentState.isSending)
            assertEquals("", sentState.inputText)
        }
    }

    @Test
    fun `sendMessage clears input and sets failed state`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val chat = createTestChat(chatId, currentUserId, otherUserId)

        val message = buildTextMessage {
            sender = chat.participants.first { it.id == currentUserId }
            recipient = chatId
            createdAt = Instant.fromEpochMilliseconds(1000)
            text = "Test message"
        }
        val repository =
            RepositoryFake(
                chat = chat,
                flowSendMessage = flowOf(
                    message.copy(deliveryStatus = DeliveryStatus.Sending(0)),
                    message.copy(
                        deliveryStatus = DeliveryStatus.Failed(
                            reason = DeliveryError.MessageTooLarge,
                        ),
                    ),
                ),
            )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.uiState.test {
            val loadingState = awaitItem()
            assertTrue(loadingState is ChatUiState.Loading)

            val readyState = awaitItem()
            assertTrue(readyState is ChatUiState.Ready)

            viewModel.updateInputText("Test message")
            val stateWithInput = awaitItem() as ChatUiState.Ready
            assertEquals("Test message", stateWithInput.inputText)

            viewModel.sendMessage()

            val sendingState = awaitItem() as ChatUiState.Ready
            assertTrue(sendingState.isSending)
            assertEquals("Test message", stateWithInput.inputText)

            val sentState = awaitItem() as ChatUiState.Ready
            assertFalse(sentState.isSending)
            assertEquals("Test message", sentState.inputText)
        }
    }

    @Test
    fun `second sendMessage keep input text`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val chat = createTestChat(chatId, currentUserId, otherUserId)

        val message = buildTextMessage {
            sender = chat.participants.first { it.id == currentUserId }
            recipient = chatId
            createdAt = Instant.fromEpochMilliseconds(1000)
            text = "Test message"
        }
        val repository =
            RepositoryFake(
                chat = chat,
                flowSendMessage = flowOf(
                    message.copy(deliveryStatus = DeliveryStatus.Sending(0)),
                    message.copy(deliveryStatus = DeliveryStatus.Sent),
                ),
            )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
        )

        viewModel.uiState.test {
            val loadingState = awaitItem()
            assertTrue(loadingState is ChatUiState.Loading)

            val readyState = awaitItem()
            assertTrue(readyState is ChatUiState.Ready)

            viewModel.updateInputText("Test message")
            val stateWithInput = awaitItem() as ChatUiState.Ready
            assertEquals("Test message", stateWithInput.inputText)

            viewModel.sendMessage()

            val sendingState = awaitItem() as ChatUiState.Ready
            assertTrue(sendingState.isSending)
            assertEquals("Test message", stateWithInput.inputText)

            val sentState = awaitItem() as ChatUiState.Ready
            assertFalse(sentState.isSending)
            assertEquals("", sentState.inputText)
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

        viewModel.uiState.test {
            val loadingState = awaitItem()
            assertTrue(loadingState is ChatUiState.Loading)

            val readyState = awaitItem()
            assertTrue(readyState is ChatUiState.Ready)
            assertNull(readyState.dialogError)

            viewModel.sendMessage() // Sending a empty text message should cause an error

            val sendingState = awaitItem()
            assertTrue(sendingState is ChatUiState.Ready)
            assertTrue(sendingState.isSending)
            assertNull(sendingState.dialogError)

            val errorState = awaitItem() as ChatUiState.Ready
            assertNotNull(errorState.dialogError)

            viewModel.dismissDialogError()

            val clearedState = awaitItem()
            assertTrue(clearedState is ChatUiState.Ready)
            assertNull(clearedState.dialogError)
        }
    }
}
