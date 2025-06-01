package timur.gilfanov.messenger.domain.usecase.message

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import timur.gilfanov.messenger.data.repository.NotImplemented
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.buildMessage
import timur.gilfanov.messenger.domain.usecase.Repository

class DeleteMessageUseCaseTest {

    private class RepositoryFake(
        private val deleteMessageResult: ResultWithError<Unit, RepositoryDeleteMessageError> =
            ResultWithError.Success(Unit),
    ) : Repository by NotImplemented() {
        override suspend fun deleteMessage(
            messageId: MessageId,
            mode: DeleteMessageMode,
        ): ResultWithError<Unit, RepositoryDeleteMessageError> = deleteMessageResult
    }

    @Test
    fun `message not found`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
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
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteMessageError.MessageNotFound>(result.error)
        assertEquals(messageId, result.error.messageId)
    }

    @Test
    fun `delete window expired rule failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
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
            rules = persistentSetOf(DeleteMessageRule.DeleteWindow(deleteWindowDuration))
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteMessageError.DeleteWindowExpired>(result.error)
        assertEquals(deleteWindowDuration, result.error.windowDuration)
    }

    @Test
    fun `sender permission check failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)

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
            rules = persistentSetOf(DeleteMessageRule.SenderCanDeleteOwn)
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = currentUser,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteMessageError.NotAuthorized>(result.error)
    }

    @Test
    fun `admin can delete any message`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)

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
            rules = persistentSetOf(
                DeleteMessageRule.AdminCanDeleteAny,
                DeleteMessageRule.SenderCanDeleteOwn,
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = admin,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Success<Unit, DeleteMessageError>>(result)
    }

    @Test
    fun `moderator can delete any message`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)

        val messageSender = buildParticipant {
            name = "MessageSender"
            joinedAt = customTime - 20.minutes
        }
        val admin = buildParticipant {
            name = "Admin"
            joinedAt = customTime - 20.minutes
            isModerator = true
        }

        val message = buildMessage {
            sender = messageSender
            createdAt = customTime - 2.minutes
        }

        val chat = buildChat {
            participants = persistentSetOf(messageSender, admin)
            messages = persistentListOf(message)
            rules = persistentSetOf(
                DeleteMessageRule.ModeratorCanDeleteAny,
                DeleteMessageRule.SenderCanDeleteOwn,
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = admin,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Success<Unit, DeleteMessageError>>(result)
    }

    @Test
    fun `message already delivered rule failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)

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
            rules = persistentSetOf(
                DeleteMessageRule.SenderCanDeleteOwn,
                DeleteMessageRule.NoDeleteAfterDelivered,
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteMessageError.MessageAlreadyDelivered>(result.error)
    }

    @Test
    fun `delete for everyone window expired`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
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
                DeleteMessageRule.SenderCanDeleteOwn,
                DeleteMessageRule.DeleteForEveryoneWindow(deleteForEveryoneWindowDuration),
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = DeleteMessageMode.FOR_EVERYONE,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteMessageError.DeleteForEveryoneWindowExpired>(result.error)
        assertEquals(deleteForEveryoneWindowDuration, result.error.windowDuration)
    }

    @Test
    fun `delete for everyone window does not apply to sender only mode`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
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
                DeleteMessageRule.SenderCanDeleteOwn,
                DeleteMessageRule.DeleteForEveryoneWindow(deleteForEveryoneWindowDuration),
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Success<Unit, DeleteMessageError>>(result)
    }

    @Test
    fun `successful delete with multiple rules`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
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
                DeleteMessageRule.SenderCanDeleteOwn,
                DeleteMessageRule.DeleteWindow(10.minutes),
                DeleteMessageRule.DeleteForEveryoneWindow(5.minutes),
                DeleteMessageRule.NoDeleteAfterDelivered,
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = DeleteMessageMode.FOR_EVERYONE,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Success<Unit, DeleteMessageError>>(result)
    }

    @Test
    fun `repository error propagation`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)

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
            rules = persistentSetOf(DeleteMessageRule.SenderCanDeleteOwn)
        }

        val errors = mapOf(
            RepositoryDeleteMessageError.NetworkNotAvailable to
                DeleteMessageError.NetworkNotAvailable,
            RepositoryDeleteMessageError.LocalError to DeleteMessageError.LocalError,
            RepositoryDeleteMessageError.RemoteError to DeleteMessageError.RemoteError,
            RepositoryDeleteMessageError.RemoteUnreachable to DeleteMessageError.RemoteUnreachable,
        )

        errors.forEach { (repoError, expectedError) ->
            val repository =
                RepositoryFake(deleteMessageResult = ResultWithError.Failure(repoError))
            val useCase = DeleteMessageUseCase(
                chat = chat,
                messageId = message.id,
                currentUser = participant,
                deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
                repository = repository,
                now = customTime,
            )

            val result = useCase()
            assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
            assertEquals(expectedError, result.error)
        }
    }

    @Test
    fun `repository returns remote error`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)

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
            rules = persistentSetOf(
                DeleteMessageRule.SenderCanDeleteOwn,
                DeleteMessageRule.DeleteWindow(10.minutes),
            )
        }

        val repository =
            RepositoryFake(
                deleteMessageResult = ResultWithError.Failure(
                    RepositoryDeleteMessageError.RemoteError,
                ),
            )

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = participant,
            deleteMode = DeleteMessageMode.FOR_EVERYONE,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
        assertEquals(DeleteMessageError.RemoteError, result.error)
    }

    @Test
    fun `admin can delete message with AdminCanDeleteAny rule`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

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
            createdAt = messageCreatedAt
        }

        val chat = buildChat {
            participants = persistentSetOf(messageSender, admin)
            messages = persistentListOf(message)
            rules = persistentSetOf(
                DeleteMessageRule.AdminCanDeleteAny,
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = admin,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Success<Unit, DeleteMessageError>>(result)
    }

    @Test
    fun `moderator can delete message with ModeratorCanDeleteAny rule`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val messageSender = buildParticipant {
            name = "MessageSender"
            joinedAt = customTime - 20.minutes
        }

        val moderator = buildParticipant {
            name = "Moderator"
            joinedAt = customTime - 20.minutes
            isModerator = true
        }

        val message = buildMessage {
            sender = messageSender
            createdAt = messageCreatedAt
        }

        val chat = buildChat {
            participants = persistentSetOf(messageSender, moderator)
            messages = persistentListOf(message)
            rules = persistentSetOf(
                DeleteMessageRule.ModeratorCanDeleteAny,
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = moderator,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Success<Unit, DeleteMessageError>>(result)
    }

    @Test
    fun `admin is subject to DeleteWindow rule`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 10.minutes
        val deleteWindowDuration = 5.minutes

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
            createdAt = messageCreatedAt
        }

        val chat = buildChat {
            participants = persistentSetOf(messageSender, admin)
            messages = persistentListOf(message)
            rules = persistentSetOf(
                DeleteMessageRule.AdminCanDeleteAny,
                DeleteMessageRule.DeleteWindow(deleteWindowDuration),
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = admin,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteMessageError.DeleteWindowExpired>(result.error)
    }

    @Test
    fun `admin is subject to DeleteForEveryoneWindow rule`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 10.minutes
        val deleteForEveryoneWindowDuration = 5.minutes

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
            createdAt = messageCreatedAt
        }

        val chat = buildChat {
            participants = persistentSetOf(messageSender, admin)
            messages = persistentListOf(message)
            rules = persistentSetOf(
                DeleteMessageRule.AdminCanDeleteAny,
                DeleteMessageRule.DeleteForEveryoneWindow(deleteForEveryoneWindowDuration),
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = admin,
            deleteMode = DeleteMessageMode.FOR_EVERYONE,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteMessageError.DeleteForEveryoneWindowExpired>(result.error)
    }

    @Test
    fun `admin is subject to NoDeleteAfterDelivered rule`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)

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
            deliveryStatus = DeliveryStatus.Delivered
        }

        val chat = buildChat {
            participants = persistentSetOf(messageSender, admin)
            messages = persistentListOf(message)
            rules = persistentSetOf(
                DeleteMessageRule.AdminCanDeleteAny,
                DeleteMessageRule.NoDeleteAfterDelivered,
            )
        }

        val repository = RepositoryFake()

        val useCase = DeleteMessageUseCase(
            chat = chat,
            messageId = message.id,
            currentUser = admin,
            deleteMode = DeleteMessageMode.FOR_SENDER_ONLY,
            repository = repository,
            now = customTime,
        )

        val result = useCase()
        assertIs<ResultWithError.Failure<Unit, DeleteMessageError>>(result)
        assertIs<DeleteMessageError.MessageAlreadyDelivered>(result.error)
    }
}
