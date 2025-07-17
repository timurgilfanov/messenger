package timur.gilfanov.messenger.ui.screen.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryError.NetworkUnavailable
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Delivered
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Failed
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Read
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageError
import timur.gilfanov.messenger.domain.usecase.participant.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.RepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.RepositoryFakeWithStatusFlow
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelMessageSendingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher: TestDispatcher get() = mainDispatcherRule.testDispatcher

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
            val message = buildTextMessage {
                sender = chat.participants.first { it.id == currentUserId }
                recipient = chatId
                this.createdAt = now
                text = "Test message"
            }
            val rep = RepositoryFakeWithStatusFlow(chat, statuses)
            val viewModel = ChatViewModel(
                chatIdUuid = chatId.id,
                currentUserIdUuid = currentUserId.id,
                sendMessageUseCase = SendMessageUseCase(rep, DeliveryStatusValidatorImpl()),
                receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(rep),
            )
            viewModel.test(this) {
                val job = runOnCreate()
                testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
                val inputTextField: TextFieldState
                awaitState().let { state ->
                    assertTrue(state is ChatUiState.Ready, "Expected Ready state, but got: $state")
                    assertEquals("", state.inputTextField.text)
                    assertFalse(state.isSending)
                    inputTextField = state.inputTextField
                }

                inputTextField.setTextAndPlaceCursorAtEnd("Test message")
                viewModel.sendMessage(message.id, now = now)
                expectStateOn<ChatUiState.Ready> { copy(isSending = true) }

                val messageUi = MessageUiModel(
                    id = message.id.id.toString(),
                    text = "Test message",
                    senderId = currentUserId.id.toString(),
                    senderName = "Current User",
                    createdAt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(1000)),
                    deliveryStatus = statuses[1],
                    isFromCurrentUser = true,
                )
                expectStateOn<ChatUiState.Ready> { copy(isSending = false) }
                assertEquals("", inputTextField.text)
                inputTextField.setTextAndPlaceCursorAtEnd("Test message 2")
                expectStateOn<ChatUiState.Ready> { copy(messages = persistentListOf(messageUi)) }
                assertEquals("Test message 2", inputTextField.text)
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
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
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

            job.cancel()
        }
    }
}
