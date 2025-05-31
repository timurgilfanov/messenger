package timur.gilfanov.messenger.domain.usecase

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule.CanNotWriteAfterJoining
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule.Debounce
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sent
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.buildMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.message.CreateMessageError
import timur.gilfanov.messenger.domain.usecase.message.CreateMessageError.DeliveryStatusAlreadySet
import timur.gilfanov.messenger.domain.usecase.message.CreateMessageError.DeliveryStatusUpdateNotValid
import timur.gilfanov.messenger.domain.usecase.message.CreateMessageError.MessageIsNotValid
import timur.gilfanov.messenger.domain.usecase.message.CreateMessageError.WaitAfterJoining
import timur.gilfanov.messenger.domain.usecase.message.CreateMessageError.WaitDebounce
import timur.gilfanov.messenger.domain.usecase.message.CreateMessageUseCase
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError

typealias ValidationResult = ResultWithError<Unit, DeliveryStatusValidationError>

class CreateMessageUseCaseTest {

    private class RepositoryFake(
        val sendMessageResult: Flow<Message> = flowOf(),
        val exception: Exception? = null,
    ) : Repository {
        override suspend fun sendMessage(message: Message): Flow<Message> {
            exception?.let { throw it }
            return sendMessageResult
        }

        override suspend fun editMessage(message: Message): Flow<Message> {
            error("Not yet implemented")
        }

        override suspend fun deleteMessage(
            messageId: MessageId,
            mode: DeleteMessageMode,
        ): ResultWithError<Unit, RepositoryDeleteMessageError> {
            error("Not yet implemented")
        }

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

    class DeliveryStatusValidatorFake(
        val validateResults: Map<Pair<DeliveryStatus?, DeliveryStatus?>, ValidationResult> =
            mapOf(),
    ) : DeliveryStatusValidator {
        override fun validate(
            currentStatus: DeliveryStatus?,
            newStatus: DeliveryStatus?,
        ): ValidationResult = validateResults[Pair(currentStatus, newStatus)] ?: Success(Unit)
    }

    @Test
    fun `test joining rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val joinedAt = customTime - 1.minutes
        val waitDuration = 5.minutes
        val remainingTime = waitDuration - (customTime - joinedAt)

        val participant = buildParticipant {
            this.joinedAt = joinedAt
            name = ""
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf(CanNotWriteAfterJoining(waitDuration))
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<WaitAfterJoining>(result.error)
            assertEquals(remainingTime, result.error.duration)
            awaitComplete()
        }
    }

    @Test
    fun `test debounce rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val lastMessageTime = customTime - 10.seconds
        val debounceDelay = 30.seconds
        val remainingTime = debounceDelay - (customTime - lastMessageTime)

        val participant = buildParticipant {
            name = ""
            this.joinedAt = customTime - 10.minutes
        }

        val lastMessage = buildMessage {
            sender = participant
            createdAt = lastMessageTime
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf(Debounce(debounceDelay))
            messages = persistentListOf(lastMessage)
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<WaitDebounce>(result.error)
            assertEquals(remainingTime, result.error.duration)
            awaitComplete()
        }
    }

    @Test
    fun `test multiple rules success`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val joinedAt = customTime - 10.minutes
        val lastMessageTime = customTime - 1.minutes

        val participant = buildParticipant {
            this.joinedAt = joinedAt
            name = ""
        }

        val lastMessage = buildMessage {
            sender = participant
            createdAt = lastMessageTime
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf(
                CanNotWriteAfterJoining(5.minutes),
                Debounce(30.seconds),
            )
            messages = persistentListOf(lastMessage)
        }

        val repository = RepositoryFake(sendMessageResult = flowOf(message))
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, null) to Success(Unit),
            ),
        )

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }
    }

    @Test
    fun `test with custom time`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val joinedAt = customTime - 10.minutes

        val participant = buildParticipant {
            this.joinedAt = joinedAt
            name = ""
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf(CanNotWriteAfterJoining(5.minutes))
        }

        val repository = RepositoryFake(sendMessageResult = flowOf(message))
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, null) to Success(Unit),
            ),
        )

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }
    }

    @Test
    fun `test with empty rules`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)

        val participant = buildParticipant {
            name = ""
            this.joinedAt = customTime - 10.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf()
        }

        val repository = RepositoryFake(sendMessageResult = flowOf(message))
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, null) to Success(Unit),
            ),
        )

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }
    }

    @Test
    @Suppress("LongMethod")
    fun `delivery status validation succeed then failed`() = runTest {
        val messageId = MessageId(UUID.randomUUID())
        val customTime = Instant.fromEpochMilliseconds(2000000)

        val participant = buildParticipant {
            name = ""
            this.joinedAt = customTime - 10.minutes
        }

        val message = buildMessage {
            id = messageId
            sender = participant
            createdAt = customTime
        }
        val sending50 = buildMessage {
            id = messageId
            sender = participant
            createdAt = customTime
            deliveryStatus = Sending(50)
        }
        val sending100 = buildMessage {
            id = messageId
            sender = participant
            createdAt = customTime
            deliveryStatus = Sending(100)
        }
        val sent = buildMessage {
            id = messageId
            sender = participant
            createdAt = customTime
            deliveryStatus = Sent
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf()
        }

        val validationError = DeliveryStatusValidationError.CannotChangeFromSentToSending
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, Sending(50)) to Success(Unit),
                Pair(Sending(50), Sent) to Success(Unit),
                Pair(Sent, Sending(100)) to Failure(validationError),
            ),
        )

        val repository = RepositoryFake(
            sendMessageResult = flow {
                emit(sending50)
                delay(1)
                emit(sent)
                delay(1)
                emit(sending100)
            },
        )

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result1 = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result1)
            assertEquals(sending50, result1.data)
            assertEquals(Sending(50), result1.data.deliveryStatus)

            val result2 = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result2)
            assertEquals(sent, result2.data)
            assertEquals(Sent, result2.data.deliveryStatus)

            val result3 = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result3)
            assertIs<DeliveryStatusUpdateNotValid>(result3.error)
            assertEquals(validationError, result3.error.error)

            awaitComplete()
        }
    }

    @Test
    fun `delivery status validation failed on first update`() = runTest {
        val messageId = MessageId(UUID.randomUUID())
        val customTime = Instant.fromEpochMilliseconds(2000000)

        val participant = buildParticipant {
            name = ""
            this.joinedAt = customTime - 10.minutes
        }

        val message = buildMessage {
            id = messageId
            sender = participant
            createdAt = customTime
        }
        val sending50 = buildMessage {
            id = messageId
            sender = participant
            createdAt = customTime
            deliveryStatus = Sending(50)
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf()
        }

        val validationError = DeliveryStatusValidationError.CannotChangeFromRead
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, Sending(50)) to Failure(validationError),
            ),
        )

        val repository = RepositoryFake(
            sendMessageResult = flow {
                emit(sending50)
            },
        )

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<DeliveryStatusUpdateNotValid>(result.error)
            assertEquals(validationError, result.error.error)
            awaitComplete()
        }
    }

    @Test
    fun `message validation failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val validationError = object : ValidationError {}

        val participant = buildParticipant {
            name = ""
            this.joinedAt = customTime - 10.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime
            validationResult = Failure(validationError)
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf()
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<MessageIsNotValid>(result.error)
            assertEquals(validationError, result.error.reason)
            awaitComplete()
        }
    }

    @Test
    fun `delivery status already set`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val existingStatus = Sending(25)

        val participant = buildParticipant {
            name = ""
            this.joinedAt = customTime - 10.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime
            deliveryStatus = existingStatus
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf()
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<DeliveryStatusAlreadySet>(result.error)
            assertEquals(existingStatus, result.error.status)
            awaitComplete()
        }
    }

    @Test
    fun `repository throws exception`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val exception = RuntimeException("Network error")

        val participant = buildParticipant {
            name = ""
            this.joinedAt = customTime - 10.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf()
        }

        val repository = RepositoryFake(exception = exception)
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val error = awaitError()
            assertEquals("Network error", error.message)
        }
    }

    @Test
    fun `no participant found`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val senderId = ParticipantId(UUID.randomUUID())
        val otherParticipantId = ParticipantId(UUID.randomUUID())

        val otherParticipant = Participant(
            id = otherParticipantId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val messageSender = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val message = buildMessage {
            sender = messageSender
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(otherParticipant)
            rules = persistentSetOf(CanNotWriteAfterJoining(5.minutes))
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val error = awaitError()
            assert(error is NoSuchElementException)
        }
    }

    @Test
    fun `last message not found`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val senderId = ParticipantId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val message = buildMessage {
            sender = participant
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf(Debounce(30.seconds))
        }

        val repository = RepositoryFake(sendMessageResult = flowOf(message))
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, null) to Success(Unit),
            ),
        )

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }
    }
}
