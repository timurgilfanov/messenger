package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.chat.repository.MarkMessagesAsReadRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.domain.usecase.message.repository.SendMessageRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFakeWithGate
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFakeWithStatusFlow
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelMessageSendingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `sending a message clears input only once`() = runTest {
        listOf(
            listOf(Sending(0), Sending(50)),
            listOf(Sending(0), DeliveryStatus.Failed(DeliveryError.NetworkUnavailable)),
            listOf(Sending(0), DeliveryStatus.Delivered),
            listOf(Sending(0), DeliveryStatus.Read),
        ).forEach { statuses ->
            val chatId = ChatId(UUID.randomUUID())
            val currentUserId = ParticipantId(UUID.randomUUID())
            val otherUserId = ParticipantId(UUID.randomUUID())
            val chat = createTestChat(chatId, currentUserId, otherUserId)
            val now = Instant.fromEpochMilliseconds(1000)
            val message = buildTextMessage {
                sender = chat.participants.first { it.id == currentUserId }
                recipient = chatId
                this.createdAt = now
                text = "Test message"
            }
            val rep = MessengerRepositoryFakeWithStatusFlow(chat, statuses)
            val getPagedMessagesUseCase = GetPagedMessagesUseCase(rep)
            val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(rep)
            val viewModel = ChatViewModel(
                chatIdUuid = chatId.id,
                currentUserIdUuid = currentUserId.id,
                savedStateHandle = SavedStateHandle(),
                sendMessageUseCase = SendMessageUseCase(rep, DeliveryStatusValidatorImpl()),
                receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(rep),
                getPagedMessagesUseCase = getPagedMessagesUseCase,
                markMessagesAsReadUseCase = markMessagesAsReadUseCase,
            )

            viewModel.effects.test {
                viewModel.state.test {
                    var readyState = awaitItem()
                    while (readyState !is ChatUiState.Ready) {
                        readyState = awaitItem()
                    }
                    assertFalse(readyState.isSending)

                    viewModel.onInputTextChanged("Test message")
                    viewModel.sendMessage(message.id, now = now)

                    val sendingState = awaitItem()
                    assertTrue(sendingState is ChatUiState.Ready)
                    assertTrue(sendingState.isSending)

                    val sentState = awaitItem()
                    assertTrue(sentState is ChatUiState.Ready)
                    assertFalse(sentState.isSending)

                    viewModel.onInputTextChanged("Test message 2")
                    awaitItem().let { state ->
                        assertTrue(
                            state is ChatUiState.Ready,
                            "Expected Ready state, but got: $state",
                        )
                    }
                    cancelAndIgnoreRemainingEvents()
                }

                assertIs<ChatSideEffect.ClearInputText>(awaitItem())
                expectNoEvents()
            }
        }
    }

    @Test
    fun `second send is queued while first is in-flight`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val now = Instant.fromEpochMilliseconds(1000)
        val id1 = MessageId(UUID.randomUUID())
        val id2 = MessageId(UUID.randomUUID())

        val rep = MessengerRepositoryFakeWithGate(chat)
        val gate1 = rep.addGate()
        val gate2 = rep.addGate()

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            savedStateHandle = SavedStateHandle(),
            sendMessageUseCase = SendMessageUseCase(rep, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(rep),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(rep),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(rep),
        )

        viewModel.state.test {
            var readyState = awaitItem()
            while (readyState !is ChatUiState.Ready) {
                readyState = awaitItem()
            }

            viewModel.onInputTextChanged("Hello")
            viewModel.sendMessage(id1, now)
            viewModel.sendMessage(id2, now)

            val isSendingState = awaitItem()
            assertTrue(isSendingState is ChatUiState.Ready)
            assertTrue(isSendingState.isSending)

            gate1.complete(Sending(0))

            // After gate1, executeSend1 finishes but pendingSendCount=1 (second still queued).
            // No isSending state change, but debounced chat update (200ms) provides next emission.
            val afterFirst = awaitItem()
            assertTrue(afterFirst is ChatUiState.Ready)
            assertTrue(afterFirst.isSending, "Should still be sending while second is in-flight")

            gate2.complete(Sending(0))

            val afterSecond = awaitItem()
            assertTrue(afterSecond is ChatUiState.Ready)
            assertFalse(afterSecond.isSending)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two rapid sends with same text are both processed (not conflated)`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val now = Instant.fromEpochMilliseconds(1000)
        val id1 = MessageId(UUID.randomUUID())
        val id2 = MessageId(UUID.randomUUID())

        val rep = MessengerRepositoryFakeWithGate(chat)
        val gate1 = rep.addGate()
        val gate2 = rep.addGate()

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            savedStateHandle = SavedStateHandle(),
            sendMessageUseCase = SendMessageUseCase(rep, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(rep),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(rep),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(rep),
        )

        viewModel.state.test {
            var readyState = awaitItem()
            while (readyState !is ChatUiState.Ready) {
                readyState = awaitItem()
            }

            viewModel.onInputTextChanged("Hello")
            viewModel.sendMessage(id1, now)
            viewModel.sendMessage(id2, now)

            val isSendingState = awaitItem()
            assertTrue(isSendingState is ChatUiState.Ready)
            assertTrue(isSendingState.isSending)

            gate1.complete(Sending(0))
            gate2.complete(Sending(0))

            val doneState = awaitItem()
            assertTrue(doneState is ChatUiState.Ready)
            assertFalse(doneState.isSending)

            cancelAndIgnoreRemainingEvents()
        }

        val messages = rep.getChat().messages
        assertTrue(messages.any { it.id == id1 }, "Message id1 should be in chat")
        assertTrue(messages.any { it.id == id2 }, "Message id2 should be in chat")
    }

    @Test
    fun `isSending becomes false after first success not after entire use case flow completes`() =
        runTest {
            val chatId = ChatId(UUID.randomUUID())
            val currentUserId = ParticipantId(UUID.randomUUID())
            val otherUserId = ParticipantId(UUID.randomUUID())
            val chat = createTestChat(chatId, currentUserId, otherUserId)
            val now = Instant.fromEpochMilliseconds(1000)
            val messageId = MessageId(UUID.randomUUID())

            val completionGate = CompletableDeferred<Unit>()
            val chatStateFlow = MutableStateFlow(chat)
            val rep = object : ChatRepository, MessageRepository {
                override suspend fun sendMessage(
                    message: Message,
                ): Flow<ResultWithError<Message, SendMessageRepositoryError>> = flow {
                    val msg = (message as TextMessage).copy(deliveryStatus = Sending(0))
                    emit(Success(msg))
                    completionGate.await()
                    emit(Success(msg.copy(deliveryStatus = DeliveryStatus.Delivered)))
                }

                override suspend fun receiveChatUpdates(
                    chatId: ChatId,
                ): Flow<ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>> =
                    chatStateFlow.map { Success(it) }

                override suspend fun flowChatList() = error("Not implemented")
                override fun isChatListUpdateApplying() = flowOf(false)
                override suspend fun createChat(chat: Chat) = error("Not implemented")
                override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
                override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
                    error("Not implemented")
                override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")
                override suspend fun markMessagesAsRead(
                    chatId: ChatId,
                    upToMessageId: MessageId,
                ): ResultWithError<Unit, MarkMessagesAsReadRepositoryError> = Success(Unit)
                override suspend fun editMessage(message: Message) = error("Not implemented")
                override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
                    error("Not implemented")
                override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> =
                    flowOf(PagingData.empty())
            }

            val viewModel = ChatViewModel(
                chatIdUuid = chatId.id,
                currentUserIdUuid = currentUserId.id,
                savedStateHandle = SavedStateHandle(),
                sendMessageUseCase = SendMessageUseCase(rep, DeliveryStatusValidatorImpl()),
                receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(rep),
                getPagedMessagesUseCase = GetPagedMessagesUseCase(rep),
                markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(rep),
            )

            viewModel.state.test {
                var readyState = awaitItem()
                while (readyState !is ChatUiState.Ready) {
                    readyState = awaitItem()
                }

                viewModel.onInputTextChanged("Hello")
                viewModel.sendMessage(messageId, now)

                val isSendingState = awaitItem()
                assertTrue(isSendingState is ChatUiState.Ready)
                assertTrue(isSendingState.isSending)

                val doneState = awaitItem()
                assertTrue(doneState is ChatUiState.Ready)
                assertFalse(
                    doneState.isSending,
                    "isSending must be false after first Success even though the use case flow has not completed yet",
                )
                assertFalse(completionGate.isCompleted, "completion gate must still be blocking")

                completionGate.complete(Unit)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismissDialogError clears error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val repository = MessengerRepositoryFake(chat = chat)

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            savedStateHandle = SavedStateHandle(),
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
            markMessagesAsReadUseCase = markMessagesAsReadUseCase,
        )

        viewModel.state.test {
            var readyState = awaitItem()
            while (readyState !is ChatUiState.Ready) {
                readyState = awaitItem()
            }
            assertNull(readyState.dialogError)

            viewModel.onInputTextChanged("")

            val validationErrorState = awaitItem()
            assertTrue(validationErrorState is ChatUiState.Ready)
            assertIs<TextValidationError.Empty>(validationErrorState.inputTextValidationError)

            // Sending an empty text message should cause an error
            viewModel.sendMessage(MessageId(UUID.randomUUID()), Instant.fromEpochMilliseconds(1000))

            val sendingState = awaitItem()
            assertTrue(sendingState is ChatUiState.Ready)
            assertTrue(sendingState.isSending)

            val errorState = awaitItem()
            assertTrue(errorState is ChatUiState.Ready)
            assertFalse(errorState.isSending)
            assertIs<ReadyError.SendMessageError>(errorState.dialogError)
            assertIs<SendMessageError.MessageIsNotValid>(
                (errorState.dialogError as ReadyError.SendMessageError).error,
            )

            viewModel.dismissDialogError()

            val clearedState = awaitItem()
            assertTrue(clearedState is ChatUiState.Ready)
            assertNull(clearedState.dialogError)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
