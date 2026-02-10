package timur.gilfanov.messenger.domain.usecase.participant.chat

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListUseCase
import timur.gilfanov.messenger.domain.usecase.chat.repository.FlowChatListRepositoryError
import timur.gilfanov.messenger.domain.usecase.chat.repository.FlowChatListRepositoryError.LocalOperationFailed
import timur.gilfanov.messenger.domain.usecase.chat.repository.MarkMessagesAsReadRepositoryError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError

@Category(Unit::class)
class FlowChatListUseCaseTest {

    private class RepositoryFake(
        val chatListFlow: Flow<ResultWithError<List<ChatPreview>, FlowChatListRepositoryError>>,
    ) : ChatRepository {
        override suspend fun flowChatList(): Flow<
            ResultWithError<List<ChatPreview>, FlowChatListRepositoryError>,
            > =
            chatListFlow

        // Implement other required ChatRepository methods as not implemented for this test
        override fun isChatListUpdateApplying() = flowOf(false)
        override suspend fun createChat(chat: Chat) = error("Not implemented")
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")
        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")
        override suspend fun receiveChatUpdates(chatId: ChatId) = error("Not implemented")
        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<kotlin.Unit, MarkMessagesAsReadRepositoryError> =
            ResultWithError.Success(kotlin.Unit)
    }

    @Test
    fun `returns chat list updates`() = runTest {
        val chat1Preview = ChatPreview.fromChat(buildChat { name = "Chat 1" })
        val chat2Preview = ChatPreview.fromChat(buildChat { name = "Chat 2" })
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Success(listOf(chat1Preview)))
                emit(ResultWithError.Success(listOf(chat1Preview, chat2Preview)))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val first = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListRepositoryError>>(first)
            assertEquals(listOf(chat1Preview), first.data)

            val second = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListRepositoryError>>(
                second,
            )
            assertEquals(listOf(chat1Preview, chat2Preview), second.data)
            awaitComplete()
        }
    }

    @Test
    fun `returns error from repository`() = runTest {
        val localError = LocalOperationFailed(LocalStorageError.Corrupted)
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Failure(localError))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<List<Chat>, FlowChatListRepositoryError>>(result)
            assertEquals(localError, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `returns empty chat list`() = runTest {
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Success(emptyList()))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<Chat>, FlowChatListRepositoryError>>(result)
            assertEquals(emptyList(), result.data)
            awaitComplete()
        }
    }

    @Test
    fun `handles multiple errors in flow`() = runTest {
        val error1 = LocalOperationFailed(LocalStorageError.Corrupted)
        val error2 = LocalOperationFailed(LocalStorageError.TemporarilyUnavailable)
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Failure(error1))
                emit(ResultWithError.Failure(error2))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Failure<List<Chat>, FlowChatListRepositoryError>>(firstResult)
            assertEquals(error1, firstResult.error)

            val secondResult = awaitItem()
            assertIs<ResultWithError.Failure<List<ChatPreview>, FlowChatListRepositoryError>>(
                secondResult,
            )
            assertEquals(error2, secondResult.error)
            awaitComplete()
        }
    }

    @Test
    fun `handles mixed success and errors`() = runTest {
        val chat = ChatPreview.fromChat(buildChat { name = "Test Chat" })
        val localError = LocalOperationFailed(LocalStorageError.Corrupted)
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Success(listOf(chat)))
                emit(ResultWithError.Failure(localError))
                emit(ResultWithError.Success(listOf(chat)))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListRepositoryError>>(
                firstResult,
            )
            assertEquals(listOf(chat), firstResult.data)

            val secondResult = awaitItem()
            assertIs<ResultWithError.Failure<List<ChatPreview>, FlowChatListRepositoryError>>(
                secondResult,
            )
            assertEquals(localError, secondResult.error)

            val thirdResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListRepositoryError>>(
                thirdResult,
            )
            assertEquals(listOf(chat), thirdResult.data)
            awaitComplete()
        }
    }

    @Test
    fun `handles different error types`() = runTest {
        val error1 = LocalOperationFailed(LocalStorageError.Corrupted)
        val error2 = LocalOperationFailed(LocalStorageError.TemporarilyUnavailable)
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Failure(error1))
                emit(ResultWithError.Failure(error2))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Failure<List<Chat>, FlowChatListRepositoryError>>(firstResult)
            assertEquals(error1, firstResult.error)

            val secondResult = awaitItem()
            assertIs<ResultWithError.Failure<List<ChatPreview>, FlowChatListRepositoryError>>(
                secondResult,
            )
            assertEquals(error2, secondResult.error)

            awaitComplete()
        }
    }
}
