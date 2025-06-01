package timur.gilfanov.messenger.domain.usecase.participant.chat

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented

class ReceiveChatUpdatesUseCaseTest {

    private class RepositoryFake(
        val chatUpdatesFlow: Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>,
    ) : ParticipantRepository by ParticipantRepositoryNotImplemented() {
        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = chatUpdatesFlow
    }

    @Test
    fun `successfully receive chat updates`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val chat = buildChat {
            id = chatId
            name = "Test Chat"
        }
        val updatedChat = buildChat {
            id = chatId
            name = "Updated Chat"
        }

        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(ResultWithError.Success(chat))
                emit(ResultWithError.Success(updatedChat))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result1 = awaitItem()
            assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(result1)
            assertEquals(chat, result1.data)
            assertEquals("Test Chat", result1.data.name)

            val result2 = awaitItem()
            assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(result2)
            assertEquals(updatedChat, result2.data)
            assertEquals("Updated Chat", result2.data.name)

            awaitComplete()
        }
    }

    @Test
    fun `handle chat not found error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(ResultWithError.Failure(ReceiveChatUpdatesError.ChatNotFound))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ReceiveChatUpdatesError.ChatNotFound>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle network not available error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(ResultWithError.Failure(ReceiveChatUpdatesError.NetworkNotAvailable))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ReceiveChatUpdatesError.NetworkNotAvailable>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle server unreachable error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(ResultWithError.Failure(ReceiveChatUpdatesError.ServerUnreachable))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ReceiveChatUpdatesError.ServerUnreachable>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle server error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(ResultWithError.Failure(ReceiveChatUpdatesError.ServerError))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ReceiveChatUpdatesError.ServerError>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle unknown error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(ResultWithError.Failure(ReceiveChatUpdatesError.UnknownError))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ReceiveChatUpdatesError.UnknownError>(result.error)
            awaitComplete()
        }
    }
}
