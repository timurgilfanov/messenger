package timur.gilfanov.messenger.domain.usecase.privileged

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.data.repository.NotImplemented
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule.OnlyAdminCanDelete
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.usecase.Repository
import timur.gilfanov.messenger.domain.usecase.priveleged.DeleteChatError
import timur.gilfanov.messenger.domain.usecase.priveleged.DeleteChatUseCase
import timur.gilfanov.messenger.domain.usecase.priveleged.RepositoryDeleteChatError

class DeleteChatUseCaseTest {

    private class RepositoryFake(
        private val deleteChatResult: ResultWithError<Unit, RepositoryDeleteChatError> =
            Success(Unit),
    ) : Repository by NotImplemented() {
        override suspend fun deleteChat(
            chatId: ChatId,
        ): ResultWithError<Unit, RepositoryDeleteChatError> = deleteChatResult
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
