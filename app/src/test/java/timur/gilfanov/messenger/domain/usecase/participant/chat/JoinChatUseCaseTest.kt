package timur.gilfanov.messenger.domain.usecase.participant.chat

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.AlreadyJoined
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.ChatClosed
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.ChatFull
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.CooldownActive
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.ExpiredInviteLink
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.InvalidInviteLink
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.LocalError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.OneToOneChatFull
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.RemoteError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.RemoteUnreachable
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.UserBlocked
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.UserNotFound

class JoinChatUseCaseTest {

    private class RepositoryFake(private val error: RepositoryJoinChatError? = null) :
        ParticipantRepository by ParticipantRepositoryNotImplemented() {
        override suspend fun joinChat(
            chatId: ChatId,
            inviteLink: String?,
        ): ResultWithError<Chat, RepositoryJoinChatError> = if (error == null) {
            Success<Chat, RepositoryJoinChatError>(buildChat { id = chatId })
        } else {
            Failure<Chat, RepositoryJoinChatError>(error)
        }
    }

    @Test
    fun `join open chat succeeds`() = runTest {
        val repo = RepositoryFake()
        val chatId = ChatId(id = UUID.randomUUID())
        val result = JoinChatUseCase(chatId, null, repo).invoke()
        assertIs<Success<Chat, JoinChatError>>(result)
        assertEquals(chatId, result.data.id)
    }

    @Test
    fun `network not available`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(NetworkNotAvailable)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<NetworkNotAvailable>(result.error)
    }

    @Test
    fun `remote unreachable`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(RemoteUnreachable)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<RemoteUnreachable>(result.error)
    }

    @Test
    fun `remote error`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(RemoteError)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<RemoteError>(result.error)
    }

    @Test
    fun `local error`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(LocalError)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<LocalError>(result.error)
    }

    @Test
    fun `chat not found`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(ChatNotFound)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<ChatNotFound>(result.error)
    }

    @Test
    fun `invalid invite link`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(InvalidInviteLink)
        val useCase = JoinChatUseCase(chatId, "invalid-link", repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<InvalidInviteLink>(result.error)
    }

    @Test
    fun `expired invite link`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(ExpiredInviteLink)
        val useCase = JoinChatUseCase(chatId, "expired-link", repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<ExpiredInviteLink>(result.error)
    }

    @Test
    fun `chat closed`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(ChatClosed)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<ChatClosed>(result.error)
    }

    @Test
    fun `already joined`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(AlreadyJoined)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<AlreadyJoined>(result.error)
    }

    @Test
    fun `chat full`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(ChatFull)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<ChatFull>(result.error)
    }

    @Test
    fun `one to one chat full`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(OneToOneChatFull)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<OneToOneChatFull>(result.error)
    }

    @Test
    fun `user not found`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(UserNotFound)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<UserNotFound>(result.error)
    }

    @Test
    fun `user blocked`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val repository = RepositoryFake(UserBlocked)
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<UserBlocked>(result.error)
    }

    @Test
    fun `cooldown active`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val cooldownDuration = 30.seconds
        val repository = RepositoryFake(CooldownActive(cooldownDuration))
        val useCase = JoinChatUseCase(chatId, null, repository)

        val result = useCase()

        assertIs<Failure<Chat, JoinChatError>>(result)
        assertIs<CooldownActive>(result.error)
        assertEquals(cooldownDuration, (result.error as CooldownActive).remaining)
    }
}
