package timur.gilfanov.messenger.domain.usecase.participant.chat

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError.ChatClosed

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
    fun `repository errors map correctly`() = runTest {
        val repo = RepositoryFake(ChatClosed)
        val chatId = ChatId(id = UUID.randomUUID())
        val result = JoinChatUseCase(chatId, null, repo).invoke()
        assertIs<Failure<Chat, JoinChatError>>(result)
        assertEquals(ChatClosed, result.error)
    }
}
