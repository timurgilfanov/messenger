package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import java.util.UUID
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelTextInputTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `text input clear Empty text validation error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val initialChat = createTestChat(chatId, currentUserId, otherUserId)

        val repository = MessengerRepositoryFake(chat = initialChat, flowSendMessage = flowOf())
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

        viewModel.test(this) {
            val job = runOnCreate()

            val initialState = awaitState()
            assertTrue(initialState is ChatUiState.Ready)
            assertNull(initialState.inputTextValidationError)

            viewModel.onInputTextChanged("")

            expectStateOn<ChatUiState.Ready> {
                copy(inputTextValidationError = TextValidationError.Empty)
            }

            viewModel.onInputTextChanged("Hi!")

            expectStateOn<ChatUiState.Ready> {
                copy(inputTextValidationError = null)
            }

            viewModel.onInputTextChanged("")

            expectStateOn<ChatUiState.Ready> {
                copy(inputTextValidationError = TextValidationError.Empty)
            }

            job.cancelAndJoin()
        }
    }
}
