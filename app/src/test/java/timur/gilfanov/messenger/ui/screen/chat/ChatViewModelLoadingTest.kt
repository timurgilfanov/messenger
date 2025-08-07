package timur.gilfanov.messenger.ui.screen.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.RepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestMessage

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelLoadingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loads chat successfully`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())
        val now = Instant.fromEpochMilliseconds(1000)
        val createdAtUi = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(1000))
        val message = createTestMessage(currentUserId, "Hello!", joinedAt = now, createdAt = now)
        val chat = createTestChat(chatId, currentUserId, otherUserId, listOf(message))

        val repository = RepositoryFake(chat = chat, flowChat = flowOf(Success(chat)))
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
            val state = awaitState()
            assertTrue { state is ChatUiState.Ready }
            assertEquals(
                ChatUiState.Ready(
                    id = chatId,
                    title = "Direct Message",
                    participants = persistentListOf(
                        ParticipantUiModel(
                            id = currentUserId,
                            name = "Current User",
                            pictureUrl = null,
                        ),
                        ParticipantUiModel(
                            id = otherUserId,
                            name = "Other User",
                            pictureUrl = null,
                        ),
                    ),
                    isGroupChat = false,
                    messages = persistentListOf(
                        MessageUiModel(
                            id = message.id.id.toString(),
                            text = "Hello!",
                            senderId = currentUserId.id.toString(),
                            senderName = "Current User",
                            createdAt = createdAtUi,
                            deliveryStatus = DeliveryStatus.Sent,
                            isFromCurrentUser = true,
                        ),
                    ),
                    inputTextField = (state as ChatUiState.Ready).inputTextField,
                    isSending = false,
                    status = ChatStatus.OneToOne(null),
                ),
                state,
            )

            job.cancelAndJoin()
        }
    }

    @Test
    fun `loads group chat successfully`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())
        val otherUserId = ParticipantId(UUID.randomUUID())

        val chat = createTestChat(chatId, currentUserId, otherUserId, isOneToOne = false)

        val repository = RepositoryFake(
            chat = chat,
            flowChat = flowOf(Success(chat)),
        )

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
            val state = awaitState()
            assertTrue { state is ChatUiState.Ready }
            val inputField = (state as ChatUiState.Ready).inputTextField
            assertEquals(
                ChatUiState.Ready(
                    id = chatId,
                    title = "Group Chat",
                    participants = persistentListOf(
                        ParticipantUiModel(
                            id = currentUserId,
                            name = "Current User",
                            pictureUrl = null,
                        ),
                        ParticipantUiModel(
                            id = otherUserId,
                            name = "Other User",
                            pictureUrl = null,
                        ),
                    ),
                    isGroupChat = true,
                    messages = persistentListOf(),
                    inputTextField = inputField,
                    isSending = false,
                    status = ChatStatus.Group(2),
                ),
                state,
            )

            job.cancelAndJoin()
        }
    }

    @Test
    fun `handles chat loading error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val currentUserId = ParticipantId(UUID.randomUUID())

        val repository = RepositoryFake(
            flowChat = flowOf(Failure(NetworkNotAvailable)),
        )

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
            expectState {
                ChatUiState.Loading(NetworkNotAvailable)
            }

            job.cancelAndJoin()
        }
    }
}
