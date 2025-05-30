package timur.gilfanov.messenger.domain.usecase.chat

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule.OnlyAdminCanDelete
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.Repository
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError

class DeleteChatUseCaseTest {

    private class RepositoryFake(
        private val deleteChatResult: ResultWithError<Unit, RepositoryDeleteChatError> =
            Success(Unit),
    ) : Repository {
        override suspend fun deleteChat(
            chatId: ChatId,
        ): ResultWithError<Unit, RepositoryDeleteChatError> = deleteChatResult

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
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> {
            error("Not yet implemented")
        }
    }

    @Test
    fun `non-admin in group chat with AdminOnly rule is forbidden`() = runTest {
        val alice = buildParticipant { isAdmin = false }
        val bob = buildParticipant { isAdmin = false }
        val carol = buildParticipant { isAdmin = false }

        val chat = buildChat {
            participants = persistentSetOf(alice, bob, carol)
            rules = persistentSetOf(OnlyAdminCanDelete)
        }

        val useCase = DeleteChatUseCase(chat, alice, RepositoryFake())
        val result = useCase()
        assertIs<Failure<Unit, DeleteChatError>>(result)
        assertIs<DeleteChatError.NotAuthorized>(result.error)
    }

    @Test
    fun `admin in group chat with AdminOnly rule succeeds`() = runTest {
        val alice = buildParticipant { isAdmin = true }
        val bob = buildParticipant { isAdmin = false }
        val carol = buildParticipant { isAdmin = false }

        val chat = buildChat {
            participants = persistentSetOf(alice, bob, carol)
            rules = persistentSetOf(OnlyAdminCanDelete)
        }

        val useCase = DeleteChatUseCase(chat, alice, RepositoryFake())
        val result = useCase()
        assertIs<Success<Unit, DeleteChatError>>(result)
    }

    @Test
    fun `non-admin in one-to-one chat with AdminOnly rule is forbidden`() = runTest {
        val alice = buildParticipant { isAdmin = false }
        val bob = buildParticipant { isAdmin = false }

        val chat = buildChat {
            participants = persistentSetOf(alice, bob)
            rules = persistentSetOf(OnlyAdminCanDelete)
        }

        val useCase = DeleteChatUseCase(chat, alice, RepositoryFake())
        val result = useCase()
        assertIs<Failure<Unit, DeleteChatError>>(result)
        assertIs<DeleteChatError.NotAuthorized>(result.error)
    }

    @Test
    fun `user not in chat participants cannot delete chat`() = runTest {
        val alice = buildParticipant { isAdmin = false }
        val bob = buildParticipant { isAdmin = false }
        val carol = buildParticipant { isAdmin = false }
        val david = buildParticipant { isAdmin = false }

        val chat = buildChat {
            participants = persistentSetOf(alice, bob, carol)
            rules = persistentSetOf(OnlyAdminCanDelete)
        }

        val useCase = DeleteChatUseCase(chat, david, RepositoryFake())
        val result = useCase()
        assertIs<Failure<Unit, DeleteChatError>>(result)
        assertIs<DeleteChatError.NotAuthorized>(result.error)
    }

    @Test
    fun `chat with no rules allows any participant to delete`() = runTest {
        val alice = buildParticipant { isAdmin = false }
        val bob = buildParticipant { isAdmin = false }

        val chat = buildChat {
            participants = persistentSetOf(alice, bob)
            rules = persistentSetOf()
        }

        val useCase = DeleteChatUseCase(chat, alice, RepositoryFake())
        val result = useCase()
        assertIs<Success<Unit, DeleteChatError>>(result)
    }

    @Test
    fun `repository errors map correctly`() = runTest {
        val alice = buildParticipant { isAdmin = true }
        val chat = buildChat { participants = persistentSetOf(alice) }

        val errors = mapOf(
            RepositoryDeleteChatError.NetworkNotAvailable to DeleteChatError.NetworkNotAvailable,
            RepositoryDeleteChatError.LocalError to DeleteChatError.LocalError,
            RepositoryDeleteChatError.RemoteError to DeleteChatError.RemoteError,
            RepositoryDeleteChatError.RemoteUnreachable to DeleteChatError.RemoteUnreachable,
            RepositoryDeleteChatError.ChatNotFound(
                chat.id,
            ) to DeleteChatError.ChatNotFound(chat.id),
        )

        errors.forEach { (repoError, expected) ->
            val repo = RepositoryFake(Failure(repoError))
            val useCase = DeleteChatUseCase(chat, alice, repo)
            val result = useCase()
            assertIs<Failure<Unit, DeleteChatError>>(result)
            assertEquals(expected, result.error)
        }
    }
}
