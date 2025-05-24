package timur.gilfanov.messenger.domain.usecase

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.ReceiveChatUpdatesError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.ReceiveChatUpdatesError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.ReceiveChatUpdatesError.ServerError
import timur.gilfanov.messenger.domain.usecase.ReceiveChatUpdatesError.ServerUnreachable
import timur.gilfanov.messenger.domain.usecase.ReceiveChatUpdatesError.UnknownError

class ReceiveChatUpdatesUseCaseTest {

    @Test
    fun `successfully receive chat updates`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val chat = mockk<Chat> {
            every { id } returns chatId
            every { name } returns "Test Chat"
        }
        val updatedChat = mockk<Chat> {
            every { id } returns chatId
            every { name } returns "Updated Chat"
        }

        val repository = mockk<Repository>()
        coEvery { repository.receiveChatUpdates(chatId) } returns flow {
            emit(Success(chat))
            emit(Success(updatedChat))
        }

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        // Act & Assert
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
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val repository = mockk<Repository>()
        coEvery { repository.receiveChatUpdates(chatId) } returns flow {
            emit(Failure(ChatNotFound))
        }

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ChatNotFound>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle network not available error`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val repository = mockk<Repository>()
        coEvery { repository.receiveChatUpdates(chatId) } returns flow {
            emit(Failure(NetworkNotAvailable))
        }

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<NetworkNotAvailable>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle server unreachable error`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val repository = mockk<Repository>()
        coEvery { repository.receiveChatUpdates(chatId) } returns flow {
            emit(Failure(ServerUnreachable))
        }

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ServerUnreachable>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle server error`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val repository = mockk<Repository>()
        coEvery { repository.receiveChatUpdates(chatId) } returns flow {
            emit(Failure(ServerError))
        }

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<ServerError>(result.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle unknown error`() = runTest {
        // Arrange
        val chatId = ChatId(UUID.randomUUID())
        val repository = mockk<Repository>()
        coEvery { repository.receiveChatUpdates(chatId) } returns flow {
            emit(Failure(UnknownError))
        }

        val useCase = ReceiveChatUpdatesUseCase(chatId, repository)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<UnknownError>(result.error)
            awaitComplete()
        }
    }
}
