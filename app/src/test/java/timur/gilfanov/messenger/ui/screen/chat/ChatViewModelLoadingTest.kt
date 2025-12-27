package timur.gilfanov.messenger.ui.screen.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError.RemoteOperationFailed
import timur.gilfanov.messenger.domain.usecase.common.RemoteError
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestMessage

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelLoadingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private data class ExpectedStateParams(
        val chatId: ChatId,
        val currentUserId: ParticipantId,
        val otherUserId: ParticipantId,
        val message: TextMessage?,
        val createdAtUi: String?,
        val actualState: ChatUiState.Ready,
        val isGroupChat: Boolean = false,
    )

    private fun createExpectedReadyState(params: ExpectedStateParams): ChatUiState.Ready =
        ChatUiState.Ready(
            id = params.chatId,
            title = if (params.isGroupChat) "Group Chat" else "Direct Message",
            participants = persistentListOf(
                ParticipantUiModel(
                    id = params.currentUserId,
                    name = "Current User",
                    pictureUrl = null,
                ),
                ParticipantUiModel(
                    id = params.otherUserId,
                    name = "Other User",
                    pictureUrl = null,
                ),
            ),
            isGroupChat = params.isGroupChat,
            messages = params.actualState.messages,
            inputTextField = params.actualState.inputTextField,
            isSending = false,
            status = if (params.isGroupChat) ChatStatus.Group(2) else ChatStatus.OneToOne(null),
        )

    @Test
    fun `loads chat successfully`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val now = Instant.fromEpochMilliseconds(1000)
        val createdAtUi = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(1000))
        val message = createTestMessage(currentUserId, "Hello!", joinedAt = now, createdAt = now)
        val chat = createTestChat(chatId, currentUserId, otherUserId, listOf(message))

        val repository = MessengerRepositoryFake(chat = chat, flowChat = flowOf(Success(chat)))
        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
            markMessagesAsReadUseCase = markMessagesAsReadUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()
            val state = awaitState()
            assertTrue { state is ChatUiState.Ready }

            val expectedState = createExpectedReadyState(
                ExpectedStateParams(
                    chatId = chatId,
                    currentUserId = currentUserId,
                    otherUserId = otherUserId,
                    message = message,
                    createdAtUi = createdAtUi,
                    actualState = state as ChatUiState.Ready,
                    isGroupChat = false,
                ),
            )
            assertEquals(expectedState, state)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `loads group chat successfully`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val chat = createTestChat(chatId, currentUserId, otherUserId, isOneToOne = false)

        val repository = MessengerRepositoryFake(
            chat = chat,
            flowChat = flowOf(Success(chat)),
        )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
            markMessagesAsReadUseCase = markMessagesAsReadUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()
            val state = awaitState()
            assertTrue { state is ChatUiState.Ready }

            val expectedState = createExpectedReadyState(
                ExpectedStateParams(
                    chatId = chatId,
                    currentUserId = currentUserId,
                    otherUserId = otherUserId,
                    message = null,
                    createdAtUi = null,
                    actualState = state as ChatUiState.Ready,
                    isGroupChat = true,
                ),
            )
            assertEquals(expectedState, state)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `handles chat loading error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())

        val repository = MessengerRepositoryFake(
            flowChat = flowOf(
                Failure(RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable)),
            ),
        )

        val sendMessageUseCase = SendMessageUseCase(repository, DeliveryStatusValidatorImpl())
        val receiveChatUpdatesUseCase = ReceiveChatUpdatesUseCase(repository)

        val getPagedMessagesUseCase = GetPagedMessagesUseCase(repository)
        val markMessagesAsReadUseCase = MarkMessagesAsReadUseCase(repository)
        val viewModel = ChatViewModel(
            chatIdUuid = chatId.id,
            currentUserIdUuid = currentUserId.id,
            sendMessageUseCase = sendMessageUseCase,
            receiveChatUpdatesUseCase = receiveChatUpdatesUseCase,
            getPagedMessagesUseCase = getPagedMessagesUseCase,
            markMessagesAsReadUseCase = markMessagesAsReadUseCase,
        )

        viewModel.test(this) {
            val job = runOnCreate()
            expectState {
                ChatUiState.Loading(RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable))
            }

            job.cancelAndJoin()
        }
    }
}
