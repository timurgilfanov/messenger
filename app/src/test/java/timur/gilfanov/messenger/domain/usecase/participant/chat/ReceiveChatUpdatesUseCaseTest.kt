package timur.gilfanov.messenger.domain.usecase.participant.chat

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError.RemoteOperationFailed
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesUseCase
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryMarkMessagesAsReadError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(Unit::class)
class ReceiveChatUpdatesUseCaseTest {

    private class RepositoryFake(
        val chatUpdatesFlow: Flow<ResultWithError<Chat, ReceiveChatUpdatesError>>,
    ) : ChatRepository {
        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> = chatUpdatesFlow

        // Implement other required ChatRepository methods as not implemented for this test
        override suspend fun flowChatList() = error("Not implemented")
        override fun isChatListUpdating() = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun createChat(chat: Chat) = error("Not implemented")
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")
        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")
        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<kotlin.Unit, RepositoryMarkMessagesAsReadError> =
            ResultWithError.Success(kotlin.Unit)
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

        val useCase = ReceiveChatUpdatesUseCase(repository)

        useCase(chatId).test {
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

        val useCase = ReceiveChatUpdatesUseCase(repository)

        useCase(chatId).test {
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
                emit(
                    ResultWithError.Failure(
                        RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
                    ),
                )
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(repository)

        useCase(chatId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<RemoteOperationFailed>(result.error)
            assertIs<RemoteError.Failed.NetworkNotAvailable>(result.error.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle server unreachable error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(
                    ResultWithError.Failure(
                        RemoteOperationFailed(RemoteError.Failed.ServiceDown),
                    ),
                )
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(repository)

        useCase(chatId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<RemoteOperationFailed>(result.error)
            assertIs<RemoteError.Failed.ServiceDown>(result.error.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle server error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(
                    ResultWithError.Failure(
                        RemoteOperationFailed(RemoteError.Unauthenticated),
                    ),
                )
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(repository)

        useCase(chatId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<RemoteOperationFailed>(result.error)
            assertIs<RemoteError.Unauthenticated>(result.error.error)
            awaitComplete()
        }
    }

    @Test
    fun `handle unknown error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val cause = RuntimeException("Unknown error")
        val repository = RepositoryFake(
            chatUpdatesFlow = flow {
                emit(
                    ResultWithError.Failure(
                        RemoteOperationFailed(
                            RemoteError.Failed.UnknownServiceError(
                                timur.gilfanov.messenger.domain.usecase.common.ErrorReason(
                                    cause.message ?: "Unknown",
                                ),
                            ),
                        ),
                    ),
                )
            },
        )

        val useCase = ReceiveChatUpdatesUseCase(repository)

        useCase(chatId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertIs<RemoteOperationFailed>(result.error)
            assertIs<RemoteError.Failed.UnknownServiceError>(result.error.error)
            awaitComplete()
        }
    }
}
