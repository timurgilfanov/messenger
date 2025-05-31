package timur.gilfanov.messenger.domain.usecase

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.ServerError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.ServerUnreachable
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.UnknownError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryDeleteChatError
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError

class ReceiveChatUpdatesUseCaseTest {

    private class RepositoryFake(
        val chatUpdatesFlow: Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>,
    ) : Repository {
        override suspend fun sendMessage(message: Message): Flow<Message> {
            error("Not yet implemented")
        }

        override suspend fun editMessage(message: Message): Flow<Message> {
            error("Not yet implemented")
        }

        override suspend fun deleteMessage(
            messageId: MessageId,
            mode: DeleteMessageMode,
        ): ResultWithError<Unit, RepositoryDeleteMessageError> {
            error("Not yet implemented")
        }

        override suspend fun createChat(
            chat: Chat,
        ): ResultWithError<Chat, RepositoryCreateChatError> {
            error("Not yet implemented")
        }

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = chatUpdatesFlow

        override suspend fun deleteChat(
            chatId: ChatId,
        ): ResultWithError<Unit, RepositoryDeleteChatError> {
            error("Not yet implemented")
        }
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
                emit(Success(chat))
                emit(Success(updatedChat))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result1 = awaitItem()
            assertIs<Success<Chat, ReceiveChatUpdatesError>>(result1)
            assertEquals(chat, result1.data)
            assertEquals("Test Chat", result1.data.name)

            val result2 = awaitItem()
            assertIs<Success<Chat, ReceiveChatUpdatesError>>(result2)
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
                emit(Failure(ChatNotFound))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ChatNotFound>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle network not available error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(Failure(NetworkNotAvailable))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<NetworkNotAvailable>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle server unreachable error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(Failure(ServerUnreachable))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ServerUnreachable>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle server error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(Failure(ServerError))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ServerError>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle unknown error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(Failure(UnknownError))
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<UnknownError>(result.error)
            awaitComplete()
        }
    }
}
