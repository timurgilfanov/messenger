package timur.gilfanov.messenger.ui.screen.chat

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidatorImpl
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadUseCase
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.chat.repository.MarkMessagesAsReadRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.ReceiveChatUpdatesRepositoryError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.GetPagedMessagesUseCase
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase
import timur.gilfanov.messenger.domain.usecase.message.repository.DeleteMessageRepositoryError
import timur.gilfanov.messenger.domain.usecase.message.repository.SendMessageRepositoryError
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFake
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.MessengerRepositoryFakeWithPaging
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_CHAT_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_CURRENT_USER_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.TEST_OTHER_USER_ID
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestChat
import timur.gilfanov.messenger.ui.screen.chat.ChatViewModelTestFixtures.createTestMessage

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(Component::class)
class ChatViewModelUpdatesTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher: TestDispatcher get() = mainDispatcherRule.testDispatcher

    private companion object {
        private val TEST_NEW_PARTICIPANT_ID =
            ParticipantId(UUID.fromString("00000000-0000-0000-0000-000000000004"))
    }

    @Test
    fun `message from other participant appears in UI state`() = runTest {
        val chatId = TEST_CHAT_ID
        val currentUserId = TEST_CURRENT_USER_ID
        val otherUserId = TEST_OTHER_USER_ID
        val now = Instant.fromEpochMilliseconds(1000)

        val initialChat = createTestChat(chatId, currentUserId, otherUserId)
        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Success(initialChat))
        val newMessage = createTestMessage(
            senderId = otherUserId,
            text = "Hello from other user!",
            joinedAt = now,
            createdAt = now,
        )
        val updatedChat = initialChat.copy(
            messages = persistentListOf(newMessage),
        )
        val repository = SequentialPagingRepository(
            chatFlow = chatFlow,
            pagingSnapshots = listOf(emptyList(), updatedChat.messages),
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
            awaitItem()

            val initialState = awaitItem()
            assertTrue(initialState is ChatUiState.Ready)
            assertEquals(emptyList(), flowOf(initialState.messages.first()).asSnapshot())

            chatFlow.value = Success(updatedChat)
            testDispatcher.scheduler.advanceTimeBy(200L)
            testDispatcher.scheduler.runCurrent()

            val updatedState = awaitItem()
            assertTrue(updatedState is ChatUiState.Ready)
            assertEquals(
                listOf("Hello from other user!"),
                flowOf(updatedState.messages.first()).asSnapshot().texts(),
            )
        }
    }

    @Test
    fun `chat metadata updates seen in UI state`() = runTest {
        val chatId = TEST_CHAT_ID
        val currentUserId = TEST_CURRENT_USER_ID
        val otherUserId = TEST_OTHER_USER_ID
        val newParticipantId = TEST_NEW_PARTICIPANT_ID
        val now = Instant.fromEpochMilliseconds(1000)

        val initialChat = createTestChat(chatId, currentUserId, otherUserId, isOneToOne = false)
        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Success(initialChat))

        val repository = MessengerRepositoryFake(flowChat = chatFlow)
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
            var initialState = awaitItem()
            while (initialState !is ChatUiState.Ready) {
                initialState = awaitItem()
            }
            assertEquals("Group Chat", initialState.title)
            assertEquals(2, initialState.participants.size)
            assertTrue(initialState.isGroupChat)
            assertEquals(ChatStatus.Group(2), initialState.status)

            // Update chat metadata: change name and add new participant
            val newParticipant = buildParticipant {
                id = newParticipantId
                name = "New Participant"
                joinedAt = now
            }
            val updatedChat = initialChat.copy(
                name = "Updated Group Chat",
                participants = persistentSetOf(
                    *initialChat.participants.toTypedArray(),
                    newParticipant,
                ),
            )

            chatFlow.value = Success(updatedChat)

            // Verify metadata changes are reflected in UI state
            val updatedState = awaitItem()
            assertTrue(updatedState is ChatUiState.Ready)
            assertEquals("Updated Group Chat", updatedState.title)
            assertEquals(3, updatedState.participants.size)
            assertTrue(updatedState.isGroupChat)
            assertEquals(ChatStatus.Group(3), updatedState.status)

            // Verify new participant appears in participants list
            val newParticipantUi = updatedState.participants.find { it.id == newParticipantId }
            assertEquals("New Participant", newParticipantUi?.name)
        }
    }

    @Test
    fun `few rapid messages result in one chat update`() = runTest {
        val chatId = TEST_CHAT_ID
        val currentUserId = TEST_CURRENT_USER_ID
        val otherUserId = TEST_OTHER_USER_ID
        val now = Instant.fromEpochMilliseconds(1000)

        val initialChat = createTestChat(chatId, currentUserId, otherUserId)
        val chatFlow =
            MutableStateFlow<ResultWithError<Chat, ReceiveChatUpdatesError>>(Success(initialChat))

        val repository = MessengerRepositoryFakeWithPaging(
            initialChat = initialChat,
            chatFlow = chatFlow,
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
            awaitItem() // Loading

            // Initial state should be ready
            val initialState = awaitItem()
            assertTrue(initialState is ChatUiState.Ready)
            assertEquals("Direct Message", initialState.title)

            // Send rapid updates within debounce window (200ms)
            val message1 =
                createTestMessage(otherUserId, "Message 1", joinedAt = now, createdAt = now)
            val message2 =
                createTestMessage(otherUserId, "Message 2", joinedAt = now, createdAt = now)
            val message3 =
                createTestMessage(otherUserId, "Final Message", joinedAt = now, createdAt = now)

            // Emit updates rapidly
            chatFlow.value = Success(
                initialChat.copy(messages = persistentListOf(message1)),
            )
            testDispatcher.scheduler.advanceTimeBy(50L) // Less than debounce delay

            chatFlow.value = Success(
                initialChat.copy(messages = persistentListOf(message1, message2)),
            )
            testDispatcher.scheduler.advanceTimeBy(50L) // Less than debounce delay

            chatFlow.value = Success(
                initialChat.copy(messages = persistentListOf(message1, message2, message3)),
            )
            testDispatcher.scheduler.advanceTimeBy(200L) // Complete debounce delay
            testDispatcher.scheduler.runCurrent()

            val updatedState = awaitItem()
            assertTrue(updatedState is ChatUiState.Ready)
            assertEquals(
                listOf("Message 1", "Message 2", "Final Message"),
                flowOf(updatedState.messages.first()).asSnapshot().texts(),
            )
        }
    }

    private fun List<Message>.texts(): List<String> = map { message ->
        assertIs<TextMessage>(message).text
    }

    private class SequentialPagingRepository(
        private val chatFlow: Flow<
            ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>,
            >,
        private val pagingSnapshots: List<List<Message>>,
    ) : ChatRepository,
        MessageRepository {

        private var getPagedMessagesCallCount = 0

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesRepositoryError>> = chatFlow

        override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> {
            val messages = pagingSnapshots.getOrElse(getPagedMessagesCallCount) {
                pagingSnapshots.last()
            }
            getPagedMessagesCallCount += 1
            return flowOf(PagingData.from(messages))
        }

        override suspend fun sendMessage(
            message: Message,
        ): Flow<ResultWithError<Message, SendMessageRepositoryError>> = flowOf(Success(message))

        override suspend fun flowChatList() = error("Not implemented")
        override fun isChatListUpdateApplying() = flowOf(false)
        override suspend fun createChat(chat: Chat) = error("Not implemented")
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")

        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")

        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")

        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<Unit, MarkMessagesAsReadRepositoryError> = ResultWithError.Success(Unit)

        override suspend fun editMessage(message: Message) = error("Not implemented")
        override suspend fun deleteMessage(
            messageId: MessageId,
            mode: DeleteMessageMode,
        ): ResultWithError<Unit, DeleteMessageRepositoryError> = error("Not implemented")
    }
}
