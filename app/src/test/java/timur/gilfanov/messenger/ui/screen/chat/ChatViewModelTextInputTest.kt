package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
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

        viewModel.state.test {
            var initialState = awaitItem()
            while (initialState !is ChatUiState.Ready) {
                initialState = awaitItem()
            }
            assertNull(initialState.inputTextValidationError)

            viewModel.onInputTextChanged("")

            val emptyErrorState = awaitItem()
            assertTrue(emptyErrorState is ChatUiState.Ready)
            assertTrue(emptyErrorState.inputTextValidationError is TextValidationError.Empty)

            viewModel.onInputTextChanged("Hi!")

            val clearedState = awaitItem()
            assertTrue(clearedState is ChatUiState.Ready)
            assertNull(clearedState.inputTextValidationError)

            viewModel.onInputTextChanged("")

            val emptyErrorState2 = awaitItem()
            assertTrue(emptyErrorState2 is ChatUiState.Ready)
            assertTrue(emptyErrorState2.inputTextValidationError is TextValidationError.Empty)
        }
    }
}
