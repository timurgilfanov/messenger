package timur.gilfanov.messenger.domain.usecase.privileged

import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule.OnlyAdminCanDelete
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.DeleteChatError
import timur.gilfanov.messenger.domain.usecase.chat.DeleteChatUseCase
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryDeleteChatError

@Category(timur.gilfanov.annotations.Unit::class)
class DeleteChatUseCaseTest {

    private class RepositoryFake(
        private val deleteChatResult: ResultWithError<Unit, RepositoryDeleteChatError> =
            Success(Unit),
    ) : ChatRepository {
        override suspend fun deleteChat(
            chatId: ChatId,
        ): ResultWithError<Unit, RepositoryDeleteChatError> = deleteChatResult

        // Implement other required ChatRepository methods as not implemented for this test
        override suspend fun flowChatList() = error("Not implemented")
        override fun isChatListUpdating() = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun createChat(chat: timur.gilfanov.messenger.domain.entity.chat.Chat) =
            error("Not implemented")
        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")
        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")
        override suspend fun receiveChatUpdates(chatId: ChatId) = error("Not implemented")
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
