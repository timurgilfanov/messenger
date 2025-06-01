package timur.gilfanov.messenger.domain.usecase.participant.chat

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.data.repository.ParticipantNotImplemented
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.AlreadyInChat
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.ChatClosed
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.ChatFull
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.CooldownActive
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.ExpiredInviteLink
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.InvalidInviteLink
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.LocalError
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.OneToOneChatFull
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.RemoteError
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.RemoteUnreachable
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.UserBlocked
import timur.gilfanov.messenger.domain.usecase.participant.chat.JoinChatError.UserNotFound

class JoinChatUseCaseTest {

    private class RepositoryFake(private val error: RepositoryJoinChatError? = null) :
        ParticipantRepository by ParticipantNotImplemented() {
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
    fun `repository errors map correctly`() = runTest {
        val repo = RepositoryFake(RepositoryJoinChatError.ChatClosed)
        val chatId = ChatId(id = UUID.randomUUID())
        val result = JoinChatUseCase(chatId, null, repo).invoke()
        assertIs<Failure<Chat, JoinChatError>>(result)
        assertEquals(ChatClosed, result.error)
    }

    @Test
    fun `all repository errors are mapped correctly`() = runTest {
        val chatId = ChatId(id = UUID.randomUUID())
        val errorMapping = listOf(
            RepositoryJoinChatError.NetworkNotAvailable to NetworkNotAvailable,
            RepositoryJoinChatError.RemoteUnreachable to RemoteUnreachable,
            RepositoryJoinChatError.RemoteError to RemoteError,
            RepositoryJoinChatError.LocalError to LocalError,
            RepositoryJoinChatError.ChatNotFound to ChatNotFound,
            RepositoryJoinChatError.UserNotFound to UserNotFound,
            RepositoryJoinChatError.AlreadyJoined to AlreadyInChat,
            RepositoryJoinChatError.ChatClosed to ChatClosed,
            RepositoryJoinChatError.ChatFull to ChatFull,
            RepositoryJoinChatError.OneToOneChatFull to OneToOneChatFull,
            RepositoryJoinChatError.UserBlocked to UserBlocked,
            RepositoryJoinChatError.InvalidInviteLink to InvalidInviteLink,
            RepositoryJoinChatError.ExpiredInviteLink to ExpiredInviteLink,
            RepositoryJoinChatError.CooldownActive(1.seconds) to CooldownActive(1.seconds),
        )
        for ((repoError, expectedError) in errorMapping) {
            val repo = RepositoryFake(repoError)
            val result = JoinChatUseCase(chatId, null, repo).invoke()
            assertIs<Failure<Chat, JoinChatError>>(result, "Expected Failure for $repoError")
            if (expectedError is CooldownActive && result.error is CooldownActive) {
                assertEquals(expectedError.remaining, result.error.remaining)
            } else {
                assertEquals(expectedError, result.error, "Mapping failed for $repoError")
            }
        }
    }
}
