package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.domain.usecase.message.repository.SendMessageRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFakeWithStatusFlow
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_CHAT_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_CURRENT_USER_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_OTHER_USER_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelMessageSendingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    companion object {
        private val TEST_MESSAGE_ID_1 =
            MessageId(UUID.fromString("00000000-0000-0000-0000-000000000004"))
        private val TEST_MESSAGE_ID_2 =
            MessageId(UUID.fromString("00000000-0000-0000-0000-000000000005"))
        private val TEST_INSTANT = Instant.fromEpochMilliseconds(1000)
    }

    @Test
    fun `invalid message text is rejected locally without sending`() = runTest {
        listOf(
            "" to TextValidationError.Empty,
            "   \n\t" to TextValidationError.Empty,
            "a".repeat(TextMessage.MAX_TEXT_LENGTH + 1) to
                TextValidationError.TooLong(TextMessage.MAX_TEXT_LENGTH),
        ).forEach { (inputText, expectedError) ->
            val chat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
            val repository = MessengerRepositoryFake(chat = chat)
            val viewModel = createViewModel(repository)

            viewModel.effects.test {
                viewModel.state.test {
                    var readyState = awaitItem()
                    while (readyState !is ChatUiState.Ready) {
                        readyState = awaitItem()
                    }
                    assertFalse(readyState.isSending)

                    if (inputText.isNotEmpty()) {
                        viewModel.onInputTextChanged(inputText)

                        val validationErrorState = awaitItem()
                        assertTrue(validationErrorState is ChatUiState.Ready)
                        assertEquals(
                            expectedError,
                            validationErrorState.inputTextValidationError,
                        )
                    }

                    viewModel.sendMessage(TEST_MESSAGE_ID_1, TEST_INSTANT)

                    val currentState = if (inputText.isEmpty()) {
                        awaitItem()
                    } else {
                        viewModel.state.value
                    }
                    assertTrue(currentState is ChatUiState.Ready)
                    assertFalse(currentState.isSending)
                    assertEquals(expectedError, currentState.inputTextValidationError)
                    assertTrue(repository.sentMessages.isEmpty())
                    expectNoEvents()
                }

                expectNoEvents()
            }
        }
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
                sender = chat.participants.first { it.isCurrentUser }
                recipient = chatId
                this.createdAt = now
                text = "Test message"
            }
            val rep = MessengerRepositoryFakeWithStatusFlow(chat, statuses)
            val getPagedMessagesUseCase = GetPagedMessagesUseCase(rep)
            val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(rep)
            val viewModel = ChatViewModel(
                chatIdUuid = chatId.id,
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
                    val state = viewModel.state.value
                    assertTrue(
                        state is ChatUiState.Ready,
                        "Expected Ready state, but got: $state",
                    )
                }

                val sentMessage = assertIs<TextMessage>(rep.sentMessages.single())
                assertEquals("Test message", sentMessage.text)
                assertIs<ChatSideEffect.ClearInputText>(awaitItem())
                expectNoEvents()
            }
        }
    }

    @Test
    fun `local send failure keeps input and shows dialog error`() = runTest {
        val chat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
        val repository = MessengerRepositoryFake(
            chat = chat,
            flowSendMessageResult = flowOf(
                ResultWithError.Failure(
                    SendMessageRepositoryError.LocalOperationFailed(
                        LocalStorageError.TemporarilyUnavailable,
                    ),
                ),
            ),
        )
        val viewModel = ChatViewModel(
            chatIdUuid = TEST_CHAT_ID.id,
            savedStateHandle = SavedStateHandle(),
            sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
        )

        viewModel.effects.test {
            viewModel.state.test {
                var readyState = awaitItem()
                while (readyState !is ChatUiState.Ready) {
                    readyState = awaitItem()
                }

                viewModel.onInputTextChanged("Test message")
                viewModel.sendMessage(TEST_MESSAGE_ID_1, TEST_INSTANT)

                val sendingState = awaitItem()
                assertTrue(sendingState is ChatUiState.Ready)
                assertTrue(sendingState.isSending)

                val errorState = awaitItem()
                assertTrue(errorState is ChatUiState.Ready)
                assertFalse(errorState.isSending)
                assertIs<ReadyError.SendMessageError>(errorState.dialogError)
                assertIs<SendMessageError.LocalOperationFailed>(
                    (errorState.dialogError as ReadyError.SendMessageError).error,
                )
            }

            expectNoEvents()
        }
    }

    @Test
    fun `remote failure after local acceptance does not show dialog error`() = runTest {
        val chat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
        val acceptedMessage = buildTextMessage {
            sender = chat.participants.first { it.isCurrentUser }
            recipient = TEST_CHAT_ID
            createdAt = TEST_INSTANT
            text = "Test message"
            deliveryStatus = Sending(0)
        }
        val repository = MessengerRepositoryFake(
            chat = chat,
            flowSendMessageResult = flowOf(
                ResultWithError.Success(acceptedMessage),
                ResultWithError.Failure(
                    SendMessageRepositoryError.RemoteOperationFailed(
                        RemoteError.Failed.NetworkNotAvailable,
                    ),
                ),
            ),
        )
        val viewModel = ChatViewModel(
            chatIdUuid = TEST_CHAT_ID.id,
            savedStateHandle = SavedStateHandle(),
            sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
        )

        viewModel.effects.test {
            viewModel.state.test {
                var readyState = awaitItem()
                while (readyState !is ChatUiState.Ready) {
                    readyState = awaitItem()
                }

                viewModel.onInputTextChanged("Test message")
                viewModel.sendMessage(acceptedMessage.id, TEST_INSTANT)

                val sendingState = awaitItem()
                assertTrue(sendingState is ChatUiState.Ready)
                assertTrue(sendingState.isSending)

                val acceptedState = awaitItem()
                assertTrue(acceptedState is ChatUiState.Ready)
                assertFalse(acceptedState.isSending)
                assertNull(acceptedState.dialogError)
            }

            assertIs<ChatSideEffect.ClearInputText>(awaitItem())
        }

        advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state is ChatUiState.Ready)
        assertNull(state.dialogError)
    }

    @Test
    fun `local storage failure after local acceptance shows dialog error`() = runTest {
        val chat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
        val acceptedMessage = buildTextMessage {
            sender = chat.participants.first { it.isCurrentUser }
            recipient = TEST_CHAT_ID
            createdAt = TEST_INSTANT
            text = "Test message"
            deliveryStatus = Sending(0)
        }
        val repository = MessengerRepositoryFake(
            chat = chat,
            flowSendMessageResult = flowOf(
                ResultWithError.Success(acceptedMessage),
                ResultWithError.Failure(
                    SendMessageRepositoryError.LocalOperationFailed(
                        LocalStorageError.TemporarilyUnavailable,
                    ),
                ),
            ),
        )
        val viewModel = ChatViewModel(
            chatIdUuid = TEST_CHAT_ID.id,
            savedStateHandle = SavedStateHandle(),
            sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
        )

        viewModel.effects.test {
            viewModel.state.test {
                var readyState = awaitItem()
                while (readyState !is ChatUiState.Ready) {
                    readyState = awaitItem()
                }

                viewModel.onInputTextChanged("Test message")
                viewModel.sendMessage(acceptedMessage.id, TEST_INSTANT)

                val sendingState = awaitItem()
                assertTrue(sendingState is ChatUiState.Ready)
                assertTrue(sendingState.isSending)

                val acceptedState = awaitItem()
                assertTrue(acceptedState is ChatUiState.Ready)
                assertFalse(acceptedState.isSending)
                assertNull(acceptedState.dialogError)

                val errorState = awaitItem()
                assertTrue(errorState is ChatUiState.Ready)
                assertIs<ReadyError.SendMessageError>(errorState.dialogError)
                assertIs<SendMessageError.LocalOperationFailed>(
                    (errorState.dialogError as ReadyError.SendMessageError).error,
                )
            }

            assertIs<ChatSideEffect.ClearInputText>(awaitItem())
        }
    }

    @Test
    fun `delivery status validation failure after local acceptance shows dialog error`() = runTest {
        val chat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
        val acceptedMessage = buildTextMessage {
            sender = chat.participants.first { it.isCurrentUser }
            recipient = TEST_CHAT_ID
            createdAt = TEST_INSTANT
            text = "Test message"
            deliveryStatus = Sending(0)
        }
        val invalidStatusMessage = buildTextMessage {
            id = acceptedMessage.id
            sender = chat.participants.first { it.isCurrentUser }
            recipient = TEST_CHAT_ID
            createdAt = TEST_INSTANT
            text = "Test message"
            deliveryStatus = null
        }
        val repository = MessengerRepositoryFake(
            chat = chat,
            flowSendMessageResult = flowOf(
                ResultWithError.Success(acceptedMessage),
                ResultWithError.Success(invalidStatusMessage),
            ),
        )
        val viewModel = ChatViewModel(
            chatIdUuid = TEST_CHAT_ID.id,
            savedStateHandle = SavedStateHandle(),
            sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
        )

        viewModel.effects.test {
            viewModel.state.test {
                var readyState = awaitItem()
                while (readyState !is ChatUiState.Ready) {
                    readyState = awaitItem()
                }

                viewModel.onInputTextChanged("Test message")
                viewModel.sendMessage(acceptedMessage.id, TEST_INSTANT)

                val sendingState = awaitItem()
                assertTrue(sendingState is ChatUiState.Ready)
                assertTrue(sendingState.isSending)

                val acceptedState = awaitItem()
                assertTrue(acceptedState is ChatUiState.Ready)
                assertFalse(acceptedState.isSending)
                assertNull(acceptedState.dialogError)

                val errorState = awaitItem()
                assertTrue(errorState is ChatUiState.Ready)
                assertIs<ReadyError.SendMessageError>(errorState.dialogError)
                assertIs<SendMessageError.DeliveryStatusUpdateNotValid>(
                    (errorState.dialogError as ReadyError.SendMessageError).error,
                )
            }

            assertIs<ChatSideEffect.ClearInputText>(awaitItem())
        }
    }

    @Test
    fun `dismissDialogError clears error`() = runTest {
        val chatId = TEST_CHAT_ID
        val currentUserId = TEST_CURRENT_USER_ID
        val otherUserId = TEST_OTHER_USER_ID

        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val repository = MessengerRepositoryFake(
            chat = chat,
            flowSendMessageResult = flowOf(
                ResultWithError.Failure(
                    SendMessageRepositoryError.LocalOperationFailed(LocalStorageError.Corrupted),
                ),
            ),
        )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
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

            viewModel.onInputTextChanged("Test message")
            viewModel.sendMessage(TEST_MESSAGE_ID_1, TEST_INSTANT)

            val sendingState = awaitItem()
            assertTrue(sendingState is ChatUiState.Ready)
            assertTrue(sendingState.isSending)

            val errorState = awaitItem()
            assertTrue(errorState is ChatUiState.Ready)
            assertFalse(errorState.isSending)
            assertIs<ReadyError.SendMessageError>(errorState.dialogError)
            assertIs<SendMessageError.LocalOperationFailed>(
                (errorState.dialogError as ReadyError.SendMessageError).error,
            )

            viewModel.dismissDialogError()

            val clearedState = awaitItem()
            assertTrue(clearedState is ChatUiState.Ready)
            assertNull(clearedState.dialogError)
        }
    }

    @Test
    fun `debounce rule is enforced before chat observer reflects accepted message`() = runTest {
        val debounceDelay = 5.seconds
        val baseChat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
        val chatWithDebounce = baseChat.copy(
            rules = persistentSetOf(CreateMessageRule.Debounce(debounceDelay)),
        )
        val repository = MessengerRepositoryFake(chat = chatWithDebounce)
        val viewModel = createViewModel(repository)

        viewModel.state.test {
            var readyState = awaitItem()
            while (readyState !is ChatUiState.Ready) {
                readyState = awaitItem()
            }
            assertFalse(readyState.isSending)

            viewModel.onInputTextChanged("Message 1")
            viewModel.sendMessage(TEST_MESSAGE_ID_1, TEST_INSTANT)

            val sendingState1 = awaitItem()
            assertTrue(sendingState1 is ChatUiState.Ready)
            assertTrue(sendingState1.isSending)

            val acceptedState = awaitItem()
            assertTrue(acceptedState is ChatUiState.Ready)
            assertFalse(acceptedState.isSending)

            val withinDebounce = TEST_INSTANT + 1.seconds
            viewModel.onInputTextChanged("Message 2")
            viewModel.sendMessage(TEST_MESSAGE_ID_2, withinDebounce)

            val sendingState2 = awaitItem()
            assertTrue(sendingState2 is ChatUiState.Ready)
            assertTrue(sendingState2.isSending)

            val errorState = awaitItem()
            assertTrue(errorState is ChatUiState.Ready)
            assertFalse(errorState.isSending)
            assertIs<ReadyError.SendMessageError>(errorState.dialogError)
            assertIs<SendMessageError.WaitDebounce>(
                (errorState.dialogError as ReadyError.SendMessageError).error,
            )
        }
    }

    @Test
    fun `remote failure of locally accepted send does not reset isSending for concurrent send`() =
        runTest {
            val chat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
            val acceptedMessageA = buildTextMessage {
                sender = chat.participants.first { it.isCurrentUser }
                recipient = TEST_CHAT_ID
                createdAt = TEST_INSTANT
                text = "Message 1"
                deliveryStatus = Sending(0)
            }

            val sendAChannel =
                Channel<ResultWithError<Message, SendMessageRepositoryError>>(Channel.UNLIMITED)
            val repository = MessengerRepositoryFake(
                chat = chat,
                perCallSendFlows = listOf(
                    sendAChannel.receiveAsFlow(),
                    channelFlow { awaitClose() },
                ),
            )
            val viewModel = createViewModel(repository)

            viewModel.effects.test {
                viewModel.state.test {
                    var readyState = awaitItem()
                    while (readyState !is ChatUiState.Ready) {
                        readyState = awaitItem()
                    }

                    viewModel.onInputTextChanged("Message 1")
                    viewModel.sendMessage(acceptedMessageA.id, TEST_INSTANT)

                    val sendingStateA = awaitItem()
                    assertTrue(sendingStateA is ChatUiState.Ready)
                    assertTrue(sendingStateA.isSending)

                    sendAChannel.send(ResultWithError.Success(acceptedMessageA))

                    val acceptedStateA = awaitItem()
                    assertTrue(acceptedStateA is ChatUiState.Ready)
                    assertFalse(acceptedStateA.isSending)

                    viewModel.onInputTextChanged("Message 2")
                    viewModel.sendMessage(TEST_MESSAGE_ID_2, TEST_INSTANT)

                    val sendingStateB = awaitItem()
                    assertTrue(sendingStateB is ChatUiState.Ready)
                    assertTrue(sendingStateB.isSending)

                    sendAChannel.send(
                        ResultWithError.Failure(
                            SendMessageRepositoryError.RemoteOperationFailed(
                                RemoteError.Failed.NetworkNotAvailable,
                            ),
                        ),
                    )
                    sendAChannel.close()
                    advanceUntilIdle()

                    expectNoEvents()
                }

                assertIs<ChatSideEffect.ClearInputText>(awaitItem())
                expectNoEvents()
            }

            val finalState = viewModel.state.value
            assertTrue(finalState is ChatUiState.Ready)
            assertTrue(finalState.isSending)
            assertNull(finalState.dialogError)
        }

    private fun createViewModel(repository: MessengerRepositoryFake): ChatViewModel = ChatViewModel(
        chatIdUuid = TEST_CHAT_ID.id,
        savedStateHandle = SavedStateHandle(),
        sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
        receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
        getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
        markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
    )
}
