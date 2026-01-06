package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat

@OptIn(ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ChatViewModelProcessDeathTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `restores chatId and currentUserId from SavedStateHandle after process death`() = runTest {
        val chatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val currentUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val otherUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000003"))

        val restoredSavedStateHandle = SavedStateHandle(
            mapOf(
                "chatId" to chatId.id.toString(),
                "currentUserId" to currentUserId.id.toString(),
            ),
        )

        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val repository = MessengerRepositoryFake(chat = chat, flowChat = flowOf(Success(chat)))

        val viewModel = ChatViewModel(
            chatIdUuid = UUID.fromString("00000000-0000-0000-0000-000000000010"),
            currentUserIdUuid = UUID.fromString("00000000-0000-0000-0000-000000000020"),
            savedStateHandle = restoredSavedStateHandle,
            sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
        )

        viewModel.test(this) {
            val job = runOnCreate()
            val state = awaitState()
            assertTrue(state is ChatUiState.Ready, "Expected Ready state, but got: $state")
            assertEquals(chatId, state.id)
            job.cancelAndJoin()
        }
    }

    @Test
    fun `saves chatId and currentUserId to SavedStateHandle on first creation`() = runTest {
        val chatId = ChatId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val currentUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000002"))
        val otherUserId = ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000003"))

        val emptySavedStateHandle = SavedStateHandle()

        val chat = createTestChat(chatId, currentUserId, otherUserId)
        val repository = MessengerRepositoryFake(chat = chat, flowChat = flowOf(Success(chat)))

        ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            savedStateHandle = emptySavedStateHandle,
            sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl()),
            receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository),
            getPagedMessagesUseCase = GetPagedMessagesUseCase(repository),
            markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository),
        )

        assertEquals(chatId.id.toString(), emptySavedStateHandle.get<String>("chatId"))
        assertEquals(
            currentUserId.id.toString(),
            emptySavedStateHandle.get<String>("currentUserId"),
        )
    }
}
