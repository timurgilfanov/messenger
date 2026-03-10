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

    companion object {
        private val TEST_CHAT_ID = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        private val TEST_CURRENT_USER_ID =
            ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        private val TEST_OTHER_USER_ID =
            ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000003"))
        private val TEST_MESSAGE_ID_1 =
            MessageId(UUID.fromString("00000000-0000-0000-0000-000000000004"))
        private val TEST_INSTANT = Instant.fromEpochMilliseconds(1000)
    }

    @Test
    fun `sending a message clears input only once`() = runTest {
        listOf(
            listOf(Sending(0), Sending(50)),
            listOf(Sending(0), DeliveryStatus.Failed(DeliveryError.NetworkUnavailable)),
            listOf(Sending(0), DeliveryStatus.Delivered),
            listOf(Sending(0), DeliveryStatus.Read),
        ).forEach { statuses ->
            val chatId = TEST_CHAT_ID
            val currentUserId = TEST_CURRENT_USER_ID
            val otherUserId = TEST_OTHER_USER_ID
            val chat = createTestChat(chatId, currentUserId, otherUserId)
            val now = TEST_INSTANT
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
    fun `dismissDialogError clears error`() = runTest {
        val chatId = TEST_CHAT_ID
        val currentUserId = TEST_CURRENT_USER_ID
        val otherUserId = TEST_OTHER_USER_ID

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
            viewModel.sendMessage(TEST_MESSAGE_ID_1, TEST_INSTANT)

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
