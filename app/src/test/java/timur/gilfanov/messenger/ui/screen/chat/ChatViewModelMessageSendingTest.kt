package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
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
                    assertTrue(state is ChatUiState.Ready, "Expected Ready state, but got: $state")
                }
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.effects.test {
                assertIs<ChatSideEffect.ClearInputText>(awaitItem())
                cancelAndIgnoreRemainingEvents()
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
            assertTrue(readyState is ChatUiState.Ready)

            viewModel.onInputTextChanged("Hello")
            viewModel.sendMessage(id1, now)
            viewModel.sendMessage(id2, now)

            val isSendingState = awaitItem()
            assertTrue(isSendingState is ChatUiState.Ready)
            assertTrue(isSendingState.isSending)

            gate1.complete(Delivered)

            // After gate1, executeSend1 finishes but pendingSendCount=1 (second still queued).
            // No isSending state change, but debounced chat update (200ms) provides next emission.
            val afterFirst = awaitItem()
            assertTrue(afterFirst is ChatUiState.Ready)
            assertTrue(afterFirst.isSending, "Should still be sending while second is in-flight")

            gate2.complete(Delivered)

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
            assertTrue(readyState is ChatUiState.Ready)

            viewModel.onInputTextChanged("Hello")
            viewModel.sendMessage(id1, now)
            viewModel.sendMessage(id2, now)

            val isSendingState = awaitItem()
            assertTrue(isSendingState is ChatUiState.Ready)
            assertTrue(isSendingState.isSending)

            gate1.complete(Delivered)
            gate2.complete(Delivered)

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
