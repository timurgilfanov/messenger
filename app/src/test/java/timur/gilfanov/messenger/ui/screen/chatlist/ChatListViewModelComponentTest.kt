package timur.gilfanov.messenger.ui.screen.chatlist

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.channels.Channel
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.NoOpLogger
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.profile.Profile
import timur.gilfanov.messenger.domain.entity.profile.UserId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListUseCase
import timur.gilfanov.messenger.domain.usecase.chat.repository.FlowChatListRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.FlowChatListRepositoryError.LocalOperationFailed
import timur.gilfanov.messenger.domain.usecase.chat.repository.MarkMessagesAsReadRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileError
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCase
import timur.gilfanov.messenger.domain.usecase.profile.ObserveProfileUseCaseStub
import timur.gilfanov.messenger.testutil.MainDispatcherRule
import timur.gilfanov.messenger.util.Logger

@Category(Component::class)
class ChatListViewModelComponentTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testUserId = UUID.randomUUID()
    private val testProfile = Profile(UserId(testUserId), "Test User", null)
    private val testObserveProfileUseCase: ObserveProfileUseCase =
        ObserveProfileUseCaseStub(flowOf(Success(testProfile)))
    private val testChatId = ChatId(UUID.randomUUID())
    private val testTimestamp = Clock.System.now()

    private fun createTestChat(
        id: ChatId = testChatId,
        name: String = "Test Chat",
        messages: List<Message> = emptyList(),
        unreadCount: Int = 0,
    ) = buildChat {
        this.id = id
        this.name = name
        this.participants = persistentSetOf(
            buildParticipant {
                this.id = ParticipantId(testUserId)
                this.name = "Test User"
                this.joinedAt = testTimestamp
            },
        )
        this.messages = persistentListOf<Message>().addAll(messages)
        this.unreadMessagesCount = unreadCount
    }

    private fun createTestMessage(text: String = "Test message") = buildTextMessage {
        this.sender = buildParticipant {
            this.id = ParticipantId(testUserId)
            this.name = "Test User"
            this.joinedAt = testTimestamp
        }
        this.text = text
        this.createdAt = testTimestamp
    }

    private class RepositoryFake(
        private val chatListFlow:
        Flow<ResultWithError<List<ChatPreview>, FlowChatListRepositoryError>> =
            flowOf(
                Success(emptyList()),
            ),
        private val updatingFlow: Flow<Boolean> = flowOf(false),
    ) : ChatRepository {

        override suspend fun flowChatList(): Flow<
            ResultWithError<List<ChatPreview>, FlowChatListRepositoryError>,
            > =
            chatListFlow

        override fun isChatListUpdateApplying(): Flow<Boolean> = updatingFlow

        override suspend fun createChat(chat: timur.gilfanov.messenger.domain.entity.chat.Chat) =
            error("Not implemented")
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")
        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")
        override suspend fun receiveChatUpdates(chatId: ChatId) = error("Not implemented")
        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<Unit, MarkMessagesAsReadRepositoryError> = error("Not implemented")
    }

    private suspend fun awaitLoadedState(
        turbine: ReceiveTurbine<ChatListScreenState>,
    ): ChatListScreenState {
        var state = turbine.awaitItem()
        while (state.isLoading) {
            state = turbine.awaitItem()
        }
        return state
    }

    private class RecordingLogger : Logger {
        private val infoMessages = Channel<Pair<String, String>>(capacity = Channel.BUFFERED)

        suspend fun awaitInfoMessage(): Pair<String, String> = infoMessages.receive()

        override fun d(tag: String, message: String) = Unit

        override fun i(tag: String, message: String) {
            infoMessages.trySend(tag to message)
        }

        override fun w(tag: String, message: String) = Unit

        override fun w(tag: String, message: String, throwable: Throwable) = Unit

        override fun e(tag: String, message: String) = Unit

        override fun e(tag: String, message: String, throwable: Throwable) = Unit
    }

    @Test
    fun `ViewModel displays empty state when no chats`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flowOf(Success(emptyList())),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testObserveProfileUseCase, useCase, repository, NoOpLogger())

        viewModel.state.test {
            val state = awaitLoadedState(this)

            assertEquals(ChatListUiState.Empty, state.uiState)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isChatListUpdateApplying)
            assertNull(state.error)
        }
    }

    @Test
    fun `ViewModel displays chat list when chats available`() = runTest {
        val testChat = createTestChat(name = "Test Chat")
        val repository = RepositoryFake(
            chatListFlow = flowOf(Success(listOf(ChatPreview.fromChat(testChat)))),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testObserveProfileUseCase, useCase, repository, NoOpLogger())

        viewModel.state.test {
            val state = awaitLoadedState(this)

            assertTrue(state.uiState is ChatListUiState.NotEmpty)
            assertEquals(1, state.uiState.chats.size)
            assertEquals("Test Chat", state.uiState.chats[0].name)
            assertEquals(false, state.isLoading)
            assertNull(state.error)
        }
    }

    @Test
    fun `ViewModel handles local error correctly`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flowOf(
                ResultWithError.Failure(LocalOperationFailed(LocalStorageError.Corrupted)),
            ),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testObserveProfileUseCase, useCase, repository, NoOpLogger())

        viewModel.state.test {
            val state = awaitLoadedState(this)

            assertTrue(state.uiState is ChatListUiState.NotEmpty)
            assertEquals(0, state.uiState.chats.size)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isChatListUpdateApplying)
            assertEquals(LocalOperationFailed(LocalStorageError.Corrupted), state.error)
        }
    }

    @Test
    fun `ViewModel updates refreshing state correctly`() = runTest {
        val updatingFlow = MutableStateFlow(false)
        val repository = RepositoryFake(
            chatListFlow = flowOf(Success(emptyList())),
            updatingFlow = updatingFlow,
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testObserveProfileUseCase, useCase, repository, NoOpLogger())

        viewModel.state.test {
            val initialState = awaitLoadedState(this)
            assertEquals(false, initialState.isChatListUpdateApplying)

            updatingFlow.value = true
            val refreshingState = awaitItem()
            assertEquals(true, refreshingState.isChatListUpdateApplying)

            updatingFlow.value = false
            val finalState = awaitItem()
            assertEquals(false, finalState.isChatListUpdateApplying)
        }
    }

    @Test
    fun `ViewModel transforms chat to UI model correctly`() = runTest {
        val message = createTestMessage("Hello world")
        val testChat = createTestChat(
            name = "Test Chat",
            messages = listOf(message),
            unreadCount = 2,
        )
        val repository = RepositoryFake(
            chatListFlow = flowOf(Success(listOf(ChatPreview.fromChat(testChat)))),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testObserveProfileUseCase, useCase, repository, NoOpLogger())

        viewModel.state.test {
            val state = awaitLoadedState(this)

            assertTrue(state.uiState is ChatListUiState.NotEmpty)
            val chatItem = state.uiState.chats[0]
            assertEquals("Test Chat", chatItem.name)
            assertEquals("Hello world", chatItem.lastMessage)
            assertEquals(2, chatItem.unreadCount)
            assertEquals(testTimestamp, chatItem.lastMessageTime)
        }
    }

    @Test
    fun `ViewModel handles chat updates correctly`() = runTest {
        val chatListFlow =
            MutableStateFlow<ResultWithError<List<ChatPreview>, FlowChatListRepositoryError>>(
                Success(listOf(ChatPreview.fromChat(createTestChat(name = "Initial Chat")))),
            )
        val repository = RepositoryFake(chatListFlow = chatListFlow)
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testObserveProfileUseCase, useCase, repository, NoOpLogger())

        viewModel.state.test {
            val initialState = awaitLoadedState(this)
            assertTrue(initialState.uiState is ChatListUiState.NotEmpty)
            val initialNotEmpty = initialState.uiState
            assertEquals("Initial Chat", initialNotEmpty.chats[0].name)

            chatListFlow.value =
                Success(listOf(ChatPreview.fromChat(createTestChat(name = "Updated Chat"))))

            val updatedState = awaitItem()
            assertTrue(updatedState.uiState is ChatListUiState.NotEmpty)
            assertEquals("Updated Chat", updatedState.uiState.chats[0].name)
        }
    }


    @Test
    fun `ViewModel emits unauthorized effect when profile observation is unauthorized`() = runTest {
        val profileUseCase = ObserveProfileUseCaseStub(
            flowOf(ResultWithError.Failure(ObserveProfileError.Unauthorized)),
        )
        val repository = RepositoryFake()
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(profileUseCase, useCase, repository, NoOpLogger())

        viewModel.effects.test {
            assertEquals(ChatListSideEffects.Unauthorized, awaitItem())
        }
    }

    @Test
    fun `ViewModel logs unauthorized profile observation error`() = runTest {
        val profileUseCase = ObserveProfileUseCaseStub(
            flowOf(ResultWithError.Failure(ObserveProfileError.Unauthorized)),
        )
        val repository = RepositoryFake()
        val useCase = FlowChatListUseCase(repository)
        val logger = RecordingLogger()
        ChatListViewModel(profileUseCase, useCase, repository, logger)

        val log = logger.awaitInfoMessage()
        assertEquals("ChatListViewModel", log.first)
        assertEquals("Profile observation failed with Unauthorized error", log.second)
    }

    @Test
    fun `ViewModel clears error on successful data load`() = runTest {
        val chatListFlow =
            MutableStateFlow<ResultWithError<List<ChatPreview>, FlowChatListRepositoryError>>(
                ResultWithError.Failure(LocalOperationFailed(LocalStorageError.Corrupted)),
            )
        val repository = RepositoryFake(chatListFlow = chatListFlow)
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testObserveProfileUseCase, useCase, repository, NoOpLogger())

        viewModel.state.test {
            val errorState = awaitLoadedState(this)
            assertEquals(
                LocalOperationFailed(LocalStorageError.Corrupted),
                errorState.error,
            )

            chatListFlow.value = Success(emptyList())

            val successState = awaitItem()
            assertNull(successState.error)
            assertEquals(false, successState.isLoading)
        }
    }

    @Test
    fun `ViewModel stops loading and refreshing on error`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flowOf(
                ResultWithError.Failure(
                    LocalOperationFailed(LocalStorageError.Corrupted),
                ),
            ),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testObserveProfileUseCase, useCase, repository, NoOpLogger())

        viewModel.state.test {
            val state = awaitLoadedState(this)

            assertEquals(false, state.isLoading)
            assertEquals(false, state.isChatListUpdateApplying)
            assertEquals(LocalOperationFailed(LocalStorageError.Corrupted), state.error)
        }
    }
}
