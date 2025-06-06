package timur.gilfanov.messenger.domain.usecase.privileged

import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule.OnlyAdminCanDelete
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant

class DeleteChatUseCaseTest {

    private class RepositoryFake(
        private val deleteChatResult: ResultWithError<Unit, RepositoryDeleteChatError> =
            Success(Unit),
    ) : PrivilegedRepository by PrivilegedRepositoryNotImplemented() {
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

        val useCase = DeleteChatUseCase(RepositoryFake())
        val result = useCase(chat, alice)
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

        val useCase = DeleteChatUseCase(RepositoryFake())
        val result = useCase(chat, alice)
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

        val useCase = DeleteChatUseCase(RepositoryFake())
        val result = useCase(chat, alice)
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

        val useCase = DeleteChatUseCase(RepositoryFake())
        val result = useCase(chat, david)
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

        val useCase = DeleteChatUseCase(RepositoryFake())
        val result = useCase(chat, alice)
        assertIs<Success<Unit, DeleteChatError>>(result)
    }
}
