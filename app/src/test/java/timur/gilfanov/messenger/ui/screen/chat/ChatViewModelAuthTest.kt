package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.auth.AuthState
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.auth.AuthRepositoryFake
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createAuthenticatedRepository
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelAuthTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `authenticated state loads chat`() = runTest {
        val chatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val currentUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val otherUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000003"))

        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val repository = MessengerRepositoryFake(chat = chat, flowChat = flowOf(Success(chat)))

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            savedStateHandle = SavedStateHandle(),
            authRepository = createAuthenticatedRepository(),
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
            assertIs<ChatUiState.Ready>(state)
        }
    }

    @Test
    fun `unauthenticated state emits Unauthorized side effect`() = runTest {
        val chatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

        val repository = MessengerRepositoryFake()
        val authRepository = AuthRepositoryFake(initialAuthState = AuthState.Unauthenticated)

        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            savedStateHandle = SavedStateHandle(),
            authRepository = authRepository,
            sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
        )

        viewModel.effects.test {
            assertIs<ChatSideEffect.Unauthorized>(awaitItem())
        }
    }
}
