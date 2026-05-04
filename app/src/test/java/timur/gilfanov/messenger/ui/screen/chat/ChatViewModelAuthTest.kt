package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_CHAT_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_CURRENT_USER_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_OTHER_USER_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelAuthTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads chat on init`() = runTest {
        val chat = createTestChat(TEST_CHAT_ID, TEST_CURRENT_USER_ID, TEST_OTHER_USER_ID)
        val repository = MessengerRepositoryFake(chat = chat, flowChat = flowOf(Success(chat)))

        val viewModel = ChatViewModel(
            chatIdUuid = TEST_CHAT_ID.id,
            savedStateHandle = SavedStateHandle(),
            sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
        )

        viewModel.state.test {
            var state = awaitItem()
            while (state !is ChatUiState.Ready) {
                state = awaitItem()
            }
            assertEquals(TEST_CHAT_ID, state.id)
            assertEquals("Direct Message", state.title)
        }
    }
}
