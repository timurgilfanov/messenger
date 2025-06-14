package timur.gilfanov.messenger.domain.usecase.participant.chat

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError.LocalError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError.NetworkNotAvailable
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError.NotParticipant
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError.RemoteError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError.RemoteUnreachable

@Category(Unit::class)
class LeaveChatUseCaseTest {

    private class RepositoryFake(val error: RepositoryLeaveChatError? = null) :
        ParticipantRepository by ParticipantRepositoryNotImplemented() {
        override suspend fun leaveChat(
            chatId: ChatId,
        ): ResultWithError<Unit, RepositoryLeaveChatError> = error?.let {
            Failure(it)
        } ?: Success(Unit)
    }

    @Test
    fun `successful chat leave`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake()
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Success<Unit, LeaveChatError>>(result)
    }

    @Test
    fun `chat not found`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(ChatNotFound)
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<ChatNotFound>(result.error)
    }

    @Test
    fun `not a participant`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(NotParticipant)
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<NotParticipant>(result.error)
    }

    @Test
    fun `network not available`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(NetworkNotAvailable)
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<NetworkNotAvailable>(result.error)
    }

    @Test
    fun `remote unreachable`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(RemoteUnreachable)
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<RemoteUnreachable>(result.error)
    }

    @Test
    fun `remote error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(RemoteError)
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<RemoteError>(result.error)
    }

    @Test
    fun `local error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(LocalError)
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<LocalError>(result.error)
    }
}
