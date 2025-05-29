package timur.gilfanov.messenger.domain.usecase

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.AdminCanDeleteAny
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.DeleteForEveryoneWindow
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.DeleteWindow
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.NoDeleteAfterDelivered
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule.SenderCanDeleteOwn
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode.FOR_EVERYONE
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode.FOR_SENDER_ONLY
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.buildMessage
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.DeleteForEveryoneWindowExpired
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.DeleteWindowExpired
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.MessageAlreadyDelivered
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.MessageNotFound
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageError.NotAuthorized
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageUseCase
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError

class DeleteMessageUseCaseTest {

    private class RepositoryFake(
        private val deleteMessageResult: ResultWithError<Unit, RepositoryDeleteMessageError> =
            Success(Unit),
    ) : Repository {
        override suspend fun sendMessage(message: Message): Flow<Message> {
            error("Not yet implemented")
        }

        override suspend fun editMessage(message: Message): Flow<Message> {
            error("Not yet implemented")
        }

        override suspend fun deleteMessage(
            messageId: MessageId,
            mode: DeleteMessageMode,
        ): ResultWithError<Unit, RepositoryDeleteMessageError> = deleteMessageResult

        override suspend fun createChat(
            chat: Chat,
        ): ResultWithError<Chat, RepositoryCreateChatError> {
            error("Not yet implemented")
        }

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> {
            error("Not yet implemented")
        }
    }

    @Test
    fun `message not found`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageId = MessageId(UUID.randomUUID())

        val participant = buildParticipant {
            joinedAt = customTime - 10.minutes
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            messages = persistentListOf()
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = messageId,
            currentUser = participant,
            deleteMode = FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<Failure<Unit, DeleteMessageError>>(result)
        assertIs<MessageNotFound>(result.error)
        assertEquals(messageId, result.error.messageId)
    }

    @Test
    fun `delete window expired rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 10.minutes
        val deleteWindowDuration = 5.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = messageCreatedAt
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            messages = persistentListOf(message)
            rules = persistentSetOf(DeleteWindow(deleteWindowDuration))
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteWindowExpired>(result.error)
        assertEquals(deleteWindowDuration, result.error.windowDuration)
    }

    @Test
    fun `sender permission check failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)

        val messageSender = buildParticipant {
            name = "MessageSender"
            joinedAt = customTime - 20.minutes
        }
        val currentUser = buildParticipant {
            name = "CurrentUser"
            joinedAt = customTime - 20.minutes
        }

        val message = buildMessage {
            sender = messageSender
            createdAt = customTime - 2.minutes
        }

        val chat = buildChat {
            participants = persistentSetOf(messageSender, currentUser)
            messages = persistentListOf(message)
            rules = persistentSetOf(SenderCanDeleteOwn)
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = currentUser,
            deleteMode = FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<Failure<Unit, DeleteMessageError>>(result)
        assertIs<NotAuthorized>(result.error)
    }

    @Test
    fun `admin can delete any message`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)

        val messageSender = buildParticipant {
            name = "MessageSender"
            joinedAt = customTime - 20.minutes
        }
        val admin = buildParticipant {
            name = "Admin"
            joinedAt = customTime - 20.minutes
            isAdmin = true
        }

        val message = buildMessage {
            sender = messageSender
            createdAt = customTime - 2.minutes
        }

        val chat = buildChat {
            participants = persistentSetOf(messageSender, admin)
            messages = persistentListOf(message)
            rules = persistentSetOf(AdminCanDeleteAny, SenderCanDeleteOwn)
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = admin,
            deleteMode = FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<Success<Unit, DeleteMessageError>>(result)
    }

    @Test
    fun `message already delivered rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime - 2.minutes
            deliveryStatus = DeliveryStatus.Delivered
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            messages = persistentListOf(message)
            rules = persistentSetOf(SenderCanDeleteOwn, NoDeleteAfterDelivered)
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<Failure<Unit, DeleteMessageError>>(result)
        assertIs<MessageAlreadyDelivered>(result.error)
    }

    @Test
    fun `delete for everyone window expired`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 10.minutes
        val deleteForEveryoneWindowDuration = 5.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = messageCreatedAt
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            messages = persistentListOf(message)
            rules = persistentSetOf(
                SenderCanDeleteOwn,
                DeleteForEveryoneWindow(deleteForEveryoneWindowDuration),
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = FOR_EVERYONE,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteForEveryoneWindowExpired>(result.error)
        assertEquals(deleteForEveryoneWindowDuration, result.error.windowDuration)
    }

    @Test
    fun `delete for everyone window does not apply to sender only mode`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 10.minutes
        val deleteForEveryoneWindowDuration = 5.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = messageCreatedAt
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            messages = persistentListOf(message)
            rules = persistentSetOf(
                SenderCanDeleteOwn,
                DeleteForEveryoneWindow(deleteForEveryoneWindowDuration),
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<Success<Unit, DeleteMessageError>>(result)
    }

    @Test
    fun `successful delete with multiple rules`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = messageCreatedAt
            deliveryStatus = DeliveryStatus.Sent
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            messages = persistentListOf(message)
            rules = persistentSetOf(
                SenderCanDeleteOwn,
                DeleteWindow(10.minutes),
                DeleteForEveryoneWindow(5.minutes),
                NoDeleteAfterDelivered,
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = FOR_EVERYONE,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<Success<Unit, DeleteMessageError>>(result)
    }

    @Test
    fun `repository error propagation`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime - 2.minutes
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            messages = persistentListOf(message)
            rules = persistentSetOf(SenderCanDeleteOwn)
        }

        val repositoryError = RepositoryDeleteMessageError.NetworkNotAvailable
        val repository = RepositoryFake(deleteMessageResult = Failure(repositoryError))

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<Failure<Unit, DeleteMessageError>>(result)
        assertEquals(DeleteMessageError.NetworkNotAvailable, result.error)
    }
}
