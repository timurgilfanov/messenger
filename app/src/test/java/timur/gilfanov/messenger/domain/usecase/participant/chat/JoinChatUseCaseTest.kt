package timur.gilfanov.messenger.domain.usecase.participant.chat

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.AlreadyJoined
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.ChatClosed
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.ChatFull
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.CooldownActive
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.ExpiredInviteLink
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.InvalidInviteLink
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.LocalOperationFailed
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.OneToOneChatFull
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.RemoteOperationFailed
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.UserBlocked
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatError.UserNotFound
import timur.gilfanov.messenger.domain.usecase.chat.JoinChatUseCase
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryMarkMessagesAsReadError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(Unit::class)
class JoinChatUseCaseTest {

    private class RepositoryFake(private val error: JoinChatError? = null) :
        ChatRepository {
        override suspend fun joinChat(
            chatId: ChatId,
            inviteLink: String?,
        ): ResultWithError<Chat, JoinChatError> = if (error == null) {
            Success<Chat, JoinChatError>(buildChat { id = chatId })
        } else {
            Failure<Chat, JoinChatError>(error)
        }

        // Implement other required ChatRepository methods as not implemented for this test
        override suspend fun flowChatList() = error("Not implemented")
        override fun isChatListUpdating() = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun createChat(chat: Chat) = error("Not implemented")
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
        override suspend fun leaveChat(chatId: ChatId) = error("Not implemented")
        override suspend fun receiveChatUpdates(chatId: ChatId) = error("Not implemented")
        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<kotlin.Unit, RepositoryMarkMessagesAsReadError> =
            ResultWithError.Success(kotlin.Unit)
    }

    @Test
    fun `join open chat succeeds`() = runTest {
        val repo = RepositoryFake()
        val chatId = ChatId(id = UUID.randomUUID())
        val useCase = JoinChatUseCase(repo)
        val result = useCase(chatId, null)
        assertIs<Success<Chat, JoinChatError>>(result)
        assertEquals(chatId, result.data.id)
    }

    @Test
    fun `network not available`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(
            RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
        )
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<RemoteOperationFailed>(result.error)
        assertIs<RemoteError.Failed.NetworkNotAvailable>(result.error.error)
    }

    @Test
    fun `remote unreachable`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(
            RemoteOperationFailed(RemoteError.Failed.ServiceDown),
        )
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<RemoteOperationFailed>(result.error)
        assertIs<RemoteError.Failed.ServiceDown>(result.error.error)
    }

    @Test
    fun `remote error`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(
            RemoteOperationFailed(RemoteError.Unauthenticated),
        )
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<RemoteOperationFailed>(result.error)
        assertIs<RemoteError.Unauthenticated>(result.error.error)
    }

    @Test
    fun `local error`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(
            LocalOperationFailed(LocalStorageError.Corrupted),
        )
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<LocalOperationFailed>(result.error)
        assertIs<LocalStorageError.Corrupted>(result.error.error)
    }

    @Test
    fun `chat not found`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(ChatNotFound)
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<ChatNotFound>(result.error)
    }

    @Test
    fun `invalid invite link`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(InvalidInviteLink)
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, "invalid-link")

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<InvalidInviteLink>(result.error)
    }

    @Test
    fun `expired invite link`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(ExpiredInviteLink)
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, "expired-link")

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<ExpiredInviteLink>(result.error)
    }

    @Test
    fun `chat closed`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(ChatClosed)
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<ChatClosed>(result.error)
    }

    @Test
    fun `already joined`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(AlreadyJoined)
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<AlreadyJoined>(result.error)
    }

    @Test
    fun `chat full`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(ChatFull)
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<ChatFull>(result.error)
    }

    @Test
    fun `one to one chat full`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(OneToOneChatFull)
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<OneToOneChatFull>(result.error)
    }

    @Test
    fun `user not found`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(UserNotFound)
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<UserNotFound>(result.error)
    }

    @Test
    fun `user blocked`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(UserBlocked)
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<UserBlocked>(result.error)
    }

    @Test
    fun `cooldown active`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val cooldownDuration = 30.seconds
        val repository = RepositoryFake(CooldownActive(cooldownDuration))
        val useCase = JoinChatUseCase(repository)

        val result = useCase(chatId, null)

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<CooldownActive>(result.error)
        assertEquals(cooldownDuration, (result.error as CooldownActive).remaining)
    }
}
