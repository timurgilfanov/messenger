package timur.gilfanov.messenger.domain.usecase.participant.chat

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented

@Category(timur.gilfanov.annotations.Unit::class)
class FlowChatListUseCaseTest {

    private class RepositoryFake(
        val chatListFlow: Flow<ResultWithError<List<ChatPreview>, FlowChatListError>>,
    ) : ParticipantRepository by ParticipantRepositoryNotImplemented() {
        override suspend fun flowChatList(): Flow<ResultWithError<List<ChatPreview>, FlowChatListError>> =
            chatListFlow
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
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(first)
            assertEquals(listOf(chat1Preview), first.data)

            val second = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(second)
            assertEquals(listOf(chat1Preview, chat2Preview), second.data)
            awaitComplete()
        }
    }

    @Test
    fun `returns error from repository`() = runTest {
        val networkError = FlowChatListError.NetworkNotAvailable
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Failure(networkError))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<List<Chat>, FlowChatListError>>(result)
            assertEquals(networkError, result.error)
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
            assertIs<ResultWithError.Success<List<Chat>, FlowChatListError>>(result)
            assertEquals(emptyList(), result.data)
            awaitComplete()
        }
    }

    @Test
    fun `handles multiple errors in flow`() = runTest {
        val networkError = FlowChatListError.NetworkNotAvailable
        val remoteError = FlowChatListError.RemoteError
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Failure(networkError))
                emit(ResultWithError.Failure(remoteError))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Failure<List<Chat>, FlowChatListError>>(firstResult)
            assertEquals(networkError, firstResult.error)

            val secondResult = awaitItem()
            assertIs<ResultWithError.Failure<List<ChatPreview>, FlowChatListError>>(secondResult)
            assertEquals(remoteError, secondResult.error)
            awaitComplete()
        }
    }

    @Test
    fun `handles mixed success and errors`() = runTest {
        val chat = ChatPreview.fromChat(buildChat { name = "Test Chat" })
        val networkError = FlowChatListError.NetworkNotAvailable
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Success(listOf(chat)))
                emit(ResultWithError.Failure(networkError))
                emit(ResultWithError.Success(listOf(chat)))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(firstResult)
            assertEquals(listOf(chat), firstResult.data)

            val secondResult = awaitItem()
            assertIs<ResultWithError.Failure<List<ChatPreview>, FlowChatListError>>(secondResult)
            assertEquals(networkError, secondResult.error)

            val thirdResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(thirdResult)
            assertEquals(listOf(chat), thirdResult.data)
            awaitComplete()
        }
    }

    @Test
    fun `handles different error types`() = runTest {
        val networkError = FlowChatListError.NetworkNotAvailable
        val remoteError = FlowChatListError.RemoteError
        val localError = FlowChatListError.LocalError
        val remoteUnreachable = FlowChatListError.RemoteUnreachable
        val repository = RepositoryFake(
            chatListFlow = flow {
                emit(ResultWithError.Failure(networkError))
                emit(ResultWithError.Failure(remoteError))
                emit(ResultWithError.Failure(localError))
                emit(ResultWithError.Failure(remoteUnreachable))
            },
        )

        val useCase = FlowChatListUseCase(repository)

        useCase().test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Failure<List<Chat>, FlowChatListError>>(firstResult)
            assertEquals(networkError, firstResult.error)

            val secondResult = awaitItem()
            assertIs<ResultWithError.Failure<List<ChatPreview>, FlowChatListError>>(secondResult)
            assertEquals(remoteError, secondResult.error)

            val thirdResult = awaitItem()
            assertIs<ResultWithError.Failure<List<Chat>, FlowChatListError>>(thirdResult)
            assertEquals(localError, thirdResult.error)

            val fourthResult = awaitItem()
            assertIs<ResultWithError.Failure<List<Chat>, FlowChatListError>>(fourthResult)
            assertEquals(remoteUnreachable, fourthResult.error)
            awaitComplete()
        }
    }
}
