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
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.usecase.chat.ChatRepository
import timur.gilfanov.messenger.domain.usecase.chat.LeaveChatError
import timur.gilfanov.messenger.domain.usecase.chat.LeaveChatError.ChatNotFound
import timur.gilfanov.messenger.domain.usecase.chat.LeaveChatError.LocalOperationFailed
import timur.gilfanov.messenger.domain.usecase.chat.LeaveChatError.NotParticipant
import timur.gilfanov.messenger.domain.usecase.chat.LeaveChatError.RemoteOperationFailed
import timur.gilfanov.messenger.domain.usecase.chat.LeaveChatUseCase
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadError
import timur.gilfanov.messenger.domain.usecase.common.LocalStorageError
import timur.gilfanov.messenger.domain.usecase.common.RemoteError

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class LeaveChatUseCaseTest {

    private class RepositoryFake(val error: LeaveChatError? = null) : ChatRepository {
        override suspend fun leaveChat(chatId: ChatId): ResultWithError<Unit, LeaveChatError> =
            error?.let {
                Failure(it)
            } ?: Success(Unit)

        // Implement other required ChatRepository methods as not implemented for this test
        override suspend fun flowChatList() = error("Not implemented")
        override fun isChatListUpdating() = kotlinx.coroutines.flow.flowOf(false)
        override suspend fun createChat(chat: timur.gilfanov.messenger.domain.entity.chat.Chat) =
            error("Not implemented")
        override suspend fun deleteChat(chatId: ChatId) = error("Not implemented")
        override suspend fun joinChat(chatId: ChatId, inviteLink: String?) =
            error("Not implemented")
        override suspend fun receiveChatUpdates(chatId: ChatId) = error("Not implemented")
        override suspend fun markMessagesAsRead(
            chatId: ChatId,
            upToMessageId: MessageId,
        ): ResultWithError<Unit, MarkMessagesAsReadError> = error("Not implemented")
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
        val repository = RepositoryFake(
            RemoteOperationFailed(RemoteError.Failed.NetworkNotAvailable),
        )
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<RemoteOperationFailed>(result.error)
        assertIs<RemoteError.Failed.NetworkNotAvailable>(result.error.error)
    }

    @Test
    fun `remote unreachable`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            RemoteOperationFailed(RemoteError.Failed.ServiceDown),
        )
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<RemoteOperationFailed>(result.error)
        assertIs<RemoteError.Failed.ServiceDown>(result.error.error)
    }

    @Test
    fun `remote error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            RemoteOperationFailed(RemoteError.Unauthenticated),
        )
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<RemoteOperationFailed>(result.error)
        assertIs<RemoteError.Unauthenticated>(result.error.error)
    }

    @Test
    fun `local error`() = runTest {
        val chatId = ChatId(UUID.randomUUID())
        val repository = RepositoryFake(
            LocalOperationFailed(LocalStorageError.Corrupted),
        )
        val useCase = LeaveChatUseCase(repository)

        val result = useCase(chatId)

        assertIs<Failure<Unit, LeaveChatError>>(result)
        assertIs<LocalOperationFailed>(result.error)
        assertIs<LocalStorageError.Corrupted>(result.error.error)
    }
}
