package timur.gilfanov.messenger.domain.usecase.chat

import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.data.repository.NotImplemented
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.usecase.Repository
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryLeaveChatError as RepositoryError

class LeaveChatUseCaseTest {

    private class RepositoryFake(val error: RepositoryError? = null) :
        Repository by NotImplemented() {
        override suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, RepositoryError> =
            error?.let {
                Failure(it)
            } ?: Success(Unit)
    }

    @Test
    fun `successful chat leave`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake()
        val useCase = LeaveChatUseCase(chatId, repository)

        val result = useCase()

        assertIs<Success<Unit, LeaveChatError>>(result)
    }

    @Test
    fun `chat not found`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(RepositoryError.ChatNotFound)
        val useCase = LeaveChatUseCase(chatId, repository)

        val result = useCase()

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<LeaveChatError.ChatNotFound>(result.error)
    }

    @Test
    fun `not a participant`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(RepositoryError.NotParticipant)
        val useCase = LeaveChatUseCase(chatId, repository)

        val result = useCase()

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<LeaveChatError.NotParticipant>(result.error)
    }

    @Test
    fun `network not available`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(RepositoryError.NetworkNotAvailable)
        val useCase = LeaveChatUseCase(chatId, repository)

        val result = useCase()

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<LeaveChatError.NetworkNotAvailable>(result.error)
    }

    @Test
    fun `remote unreachable`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(RepositoryError.RemoteUnreachable)
        val useCase = LeaveChatUseCase(chatId, repository)

        val result = useCase()

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<LeaveChatError.RemoteUnreachable>(result.error)
    }

    @Test
    fun `remote error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(RepositoryError.RemoteError)
        val useCase = LeaveChatUseCase(chatId, repository)

        val result = useCase()

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<LeaveChatError.RemoteError>(result.error)
    }

    @Test
    fun `local error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(RepositoryError.LocalError)
        val useCase = LeaveChatUseCase(chatId, repository)

        val result = useCase()

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<LeaveChatError.LocalError>(result.error)
    }
}
