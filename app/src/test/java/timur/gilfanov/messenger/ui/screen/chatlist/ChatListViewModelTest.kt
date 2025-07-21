package timur.gilfanov.messenger.ui.screen.chatlist

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.orbitmvi.orbit.test.test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListUseCase
import timur.gilfanov.messenger.testutil.MainDispatcherRule

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Category(timur.gilfanov.annotations.Component::class)
class ChatListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher: TestDispatcher get() = mainDispatcherRule.testDispatcher

    private val testUserId = UUID.randomUUID()
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
        private val chatListFlow: Flow<ResultWithError<List<Chat>, FlowChatListError>> = flowOf(
            Success(emptyList()),
        ),
        private val updatingFlow: Flow<Boolean> = flowOf(false),
    ) : ParticipantRepository by ParticipantRepositoryNotImplemented() {

        override suspend fun flowChatList(): Flow<ResultWithError<List<Chat>, FlowChatListError>> =
            chatListFlow

        override fun isChatListUpdating(): Flow<Boolean> = updatingFlow
    }

    @Test
    fun `ViewModel displays empty state when no chats`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flowOf(Success(emptyList())),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
            val state = awaitState()

            assertEquals(ChatListUiState.Empty, state.uiState)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isRefreshing)
            assertNull(state.error)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ViewModel displays chat list when chats available`() = runTest {
        val testChat = createTestChat(name = "Test Chat")
        val repository = RepositoryFake(
            chatListFlow = flowOf(Success(listOf(testChat))),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
            val state = awaitState()

            assertTrue(state.uiState is ChatListUiState.NotEmpty)
            assertEquals(1, state.uiState.chats.size)
            assertEquals("Test Chat", state.uiState.chats[0].name)
            assertEquals(false, state.isLoading)
            assertNull(state.error)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ViewModel handles network error correctly`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flowOf(ResultWithError.Failure(FlowChatListError.NetworkNotAvailable)),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
            val state = awaitState()

            assertEquals(ChatListUiState.Empty, state.uiState)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isRefreshing)
            assertEquals(FlowChatListError.NetworkNotAvailable, state.error)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ViewModel handles server error correctly`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flowOf(ResultWithError.Failure(FlowChatListError.RemoteError)),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
            val state = awaitState()

            assertEquals(ChatListUiState.Empty, state.uiState)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isRefreshing)
            assertEquals(FlowChatListError.RemoteError, state.error)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ViewModel handles server unreachable error correctly`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flowOf(ResultWithError.Failure(FlowChatListError.RemoteUnreachable)),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
            val state = awaitState()

            assertEquals(ChatListUiState.Empty, state.uiState)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isRefreshing)
            assertEquals(FlowChatListError.RemoteUnreachable, state.error)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ViewModel handles local error correctly`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flowOf(ResultWithError.Failure(FlowChatListError.LocalError)),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
            val state = awaitState()

            assertEquals(ChatListUiState.Empty, state.uiState)
            assertEquals(false, state.isLoading)
            assertEquals(false, state.isRefreshing)
            assertEquals(FlowChatListError.LocalError, state.error)

            job.cancelAndJoin()
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
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            // Initial state
            val initialState = awaitState()
            assertEquals(false, initialState.isRefreshing)

            // Simulate refreshing
            updatingFlow.value = true
            testDispatcher.scheduler.advanceUntilIdle()
            val refreshingState = awaitState()
            assertEquals(true, refreshingState.isRefreshing)

            // Stop refreshing
            updatingFlow.value = false
            testDispatcher.scheduler.advanceUntilIdle()
            val finalState = awaitState()
            assertEquals(false, finalState.isRefreshing)

            job.cancelAndJoin()
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
            chatListFlow = flowOf(Success(listOf(testChat))),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
            val state = awaitState()

            assertTrue(state.uiState is ChatListUiState.NotEmpty)
            val chatItem = state.uiState.chats[0]
            assertEquals("Test Chat", chatItem.name)
            assertEquals("Hello world", chatItem.lastMessage)
            assertEquals(2, chatItem.unreadCount)
            assertEquals(testTimestamp, chatItem.lastMessageTime)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ViewModel handles chat updates correctly`() = runTest {
        val chatListFlow = MutableStateFlow<ResultWithError<List<Chat>, FlowChatListError>>(
            Success(listOf(createTestChat(name = "Initial Chat"))),
        )
        val repository = RepositoryFake(chatListFlow = chatListFlow)
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            // Initial state
            val initialState = awaitState()
            assertTrue(initialState.uiState is ChatListUiState.NotEmpty)
            val initialNotEmpty = initialState.uiState
            assertEquals("Initial Chat", initialNotEmpty.chats[0].name)

            // Update chat list
            chatListFlow.value = Success(listOf(createTestChat(name = "Updated Chat")))
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedState = awaitState()
            assertTrue(updatedState.uiState is ChatListUiState.NotEmpty)
            assertEquals("Updated Chat", updatedState.uiState.chats[0].name)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ViewModel clears error on successful data load`() = runTest {
        val chatListFlow = MutableStateFlow<ResultWithError<List<Chat>, FlowChatListError>>(
            ResultWithError.Failure(FlowChatListError.NetworkNotAvailable),
        )
        val repository = RepositoryFake(chatListFlow = chatListFlow)
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete

            // Initial error state
            val errorState = awaitState()
            assertEquals(FlowChatListError.NetworkNotAvailable, errorState.error)

            // Fix the error with successful data
            chatListFlow.value = Success(emptyList())
            testDispatcher.scheduler.advanceUntilIdle()

            val successState = awaitState()
            assertNull(successState.error)
            assertEquals(false, successState.isLoading)

            job.cancelAndJoin()
        }
    }

    @Test
    fun `ViewModel stops loading and refreshing on error`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flowOf(ResultWithError.Failure(FlowChatListError.NetworkNotAvailable)),
        )
        val useCase = FlowChatListUseCase(repository)
        val viewModel = ChatListViewModel(testUserId, useCase, repository)

        viewModel.test(this) {
            val job = runOnCreate()
            testDispatcher.scheduler.advanceUntilIdle() // Allow debounce to complete
            val state = awaitState()

            assertEquals(false, state.isLoading)
            assertEquals(false, state.isRefreshing)
            assertEquals(FlowChatListError.NetworkNotAvailable, state.error)

            job.cancelAndJoin()
        }
    }
}
