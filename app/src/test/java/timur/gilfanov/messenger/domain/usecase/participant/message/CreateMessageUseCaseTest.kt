package timur.gilfanov.messenger.domain.usecase.participant.message

import androidx.paging.PagingData
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.buildMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.message.MessageRepository
import timur.gilfanov.messenger.domain.usecase.message.RepositorySendMessageError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageError
import timur.gilfanov.messenger.domain.usecase.message.SendMessageUseCase

typealias ValidationResult = ResultWithError<Unit, DeliveryStatusValidationError>

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class CreateMessageUseCaseTest {

    private class RepositoryFake(
        val sendMessageResult: Flow<ResultWithError<Message, RepositorySendMessageError>> =
            flowOf(),
        val exception: Exception? = null,
    ) : MessageRepository {
        override suspend fun sendMessage(
            message: Message,
        ): Flow<ResultWithError<Message, RepositorySendMessageError>> {
            exception?.let { throw it }
            return sendMessageResult
        }

        // Implement other required MessageRepository methods as not implemented for this test
        override suspend fun editMessage(message: Message) = error("Not implemented")
        override suspend fun deleteMessage(messageId: MessageId, mode: DeleteMessageMode) =
            error("Not implemented")

        override fun getPagedMessages(chatId: ChatId): Flow<PagingData<Message>> =
            error("Not implemented")
    }

    class DeliveryStatusValidatorFake(
        val validateResults: Map<Pair<DeliveryStatus?, DeliveryStatus?>, ValidationResult> =
            mapOf(),
    ) : DeliveryStatusValidator {
        override fun validate(
            currentStatus: DeliveryStatus?,
            newStatus: DeliveryStatus?,
        ): ValidationResult =
            validateResults[Pair(currentStatus, newStatus)] ?: ResultWithError.Success(
                Unit,
            )
    }

    @Test
    fun `test joining rule failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
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
            rules = persistentSetOf(CreateMessageRule.CanNotWriteAfterJoining(waitDuration))
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, SendMessageError>>(result)
            assertIs<SendMessageError.WaitAfterJoining>(result.error)
            assertEquals(remainingTime, result.error.duration)
            awaitComplete()
        }
    }

    @Test
    fun `test debounce rule failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
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
            rules = persistentSetOf(CreateMessageRule.Debounce(debounceDelay))
            messages = persistentListOf(lastMessage)
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, SendMessageError>>(result)
            assertIs<SendMessageError.WaitDebounce>(result.error)
            assertEquals(remainingTime, result.error.duration)
            awaitComplete()
        }
    }

    @Test
    fun `test multiple rules success`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
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
                CreateMessageRule.CanNotWriteAfterJoining(5.minutes),
                CreateMessageRule.Debounce(30.seconds),
            )
            messages = persistentListOf(lastMessage)
        }

        val repository =
            RepositoryFake(sendMessageResult = flowOf(ResultWithError.Success(message)))
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, null) to ResultWithError.Success(Unit),
            ),
        )

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Message, SendMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }
    }

    @Test
    fun `test with custom time`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)
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
            rules = persistentSetOf(CreateMessageRule.CanNotWriteAfterJoining(5.minutes))
        }

        val repository =
            RepositoryFake(sendMessageResult = flowOf(ResultWithError.Success(message)))
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, null) to ResultWithError.Success(Unit),
            ),
        )

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Message, SendMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }
    }

    @Test
    fun `test with empty rules`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)

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
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>()
        }

        val repository =
            RepositoryFake(sendMessageResult = flowOf(ResultWithError.Success(message)))
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, null) to ResultWithError.Success(Unit),
            ),
        )

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Message, SendMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }
    }

    @Test
    @Suppress("LongMethod")
    fun `delivery status validation succeed then failed`() = runTest {
        val messageId = MessageId(UUID.randomUUID())
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)

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
            deliveryStatus = DeliveryStatus.Sending(50)
        }
        val sending100 = buildMessage {
            id = messageId
            sender = participant
            createdAt = customTime
            deliveryStatus = DeliveryStatus.Sending(100)
        }
        val sent = buildMessage {
            id = messageId
            sender = participant
            createdAt = customTime
            deliveryStatus = DeliveryStatus.Sent
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>()
        }

        val validationError = DeliveryStatusValidationError.CannotChangeFromSentToSending
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, DeliveryStatus.Sending(50)) to ResultWithError.Success(Unit),
                Pair(DeliveryStatus.Sending(50), DeliveryStatus.Sent) to ResultWithError.Success(
                    Unit,
                ),
                Pair(DeliveryStatus.Sent, DeliveryStatus.Sending(100)) to ResultWithError.Failure(
                    validationError,
                ),
            ),
        )

        val repository = RepositoryFake(
            sendMessageResult = flow {
                emit(ResultWithError.Success(sending50))
                delay(1)
                emit(ResultWithError.Success(sent))
                delay(1)
                emit(ResultWithError.Success(sending100))
            },
        )

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result1 = awaitItem()
            assertIs<ResultWithError.Success<Message, SendMessageError>>(result1)
            assertEquals(sending50, result1.data)
            assertEquals(DeliveryStatus.Sending(50), result1.data.deliveryStatus)

            val result2 = awaitItem()
            assertIs<ResultWithError.Success<Message, SendMessageError>>(result2)
            assertEquals(sent, result2.data)
            assertEquals(DeliveryStatus.Sent, result2.data.deliveryStatus)

            val result3 = awaitItem()
            assertIs<ResultWithError.Failure<Message, SendMessageError>>(result3)
            assertIs<SendMessageError.DeliveryStatusUpdateNotValid>(result3.error)
            assertEquals(validationError, result3.error.error)

            awaitComplete()
        }
    }

    @Test
    fun `delivery status validation failed on first update`() = runTest {
        val messageId = MessageId(UUID.randomUUID())
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)

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
            deliveryStatus = DeliveryStatus.Sending(50)
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>()
        }

        val validationError = DeliveryStatusValidationError.CannotChangeFromRead
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, DeliveryStatus.Sending(50)) to ResultWithError.Failure(validationError),
            ),
        )

        val repository = RepositoryFake(
            sendMessageResult = flow {
                emit(ResultWithError.Success(sending50))
            },
        )

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, SendMessageError>>(result)
            assertIs<SendMessageError.DeliveryStatusUpdateNotValid>(result.error)
            assertEquals(validationError, result.error.error)
            awaitComplete()
        }
    }

    @Test
    fun `message validation failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)
        val validationError = object : ValidationError {}

        val participant = buildParticipant {
            name = ""
            this.joinedAt = customTime - 10.minutes
        }

        val message = buildMessage {
            sender = participant
            createdAt = customTime
            validationResult = ResultWithError.Failure(validationError)
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>()
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, SendMessageError>>(result)
            assertIs<SendMessageError.MessageIsNotValid>(result.error)
            assertEquals(validationError, result.error.reason)
            awaitComplete()
        }
    }

    @Test
    fun `delivery status already set`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)
        val existingStatus = DeliveryStatus.Sending(25)

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
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>()
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, SendMessageError>>(result)
            assertIs<SendMessageError.DeliveryStatusAlreadySet>(result.error)
            assertEquals(existingStatus, result.error.status)
            awaitComplete()
        }
    }

    @Test
    fun `repository throws exception`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)
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
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>()
        }

        val repository = RepositoryFake(exception = exception)
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val error = awaitError()
            assertEquals("Network error", error.message)
        }
    }

    @Test
    fun `no participant found`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)
        val senderId = ParticipantId(UUID.randomUUID())
        val otherParticipantId = ParticipantId(UUID.randomUUID())

        val otherParticipant = Participant(
            id = otherParticipantId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
            onlineAt = null,
        )

        val messageSender = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
            onlineAt = null,
        )

        val message = buildMessage {
            sender = messageSender
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(otherParticipant)
            rules = persistentSetOf(CreateMessageRule.CanNotWriteAfterJoining(5.minutes))
        }

        val repository = RepositoryFake()
        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val error = awaitError()
            assert(error is NoSuchElementException)
        }
    }

    @Test
    fun `last message not found`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(2000000)
        val senderId = ParticipantId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
            onlineAt = null,
        )

        val message = buildMessage {
            sender = participant
            createdAt = customTime
        }

        val chat = buildChat {
            participants = persistentSetOf(participant)
            rules = persistentSetOf(CreateMessageRule.Debounce(30.seconds))
        }

        val repository =
            RepositoryFake(sendMessageResult = flowOf(ResultWithError.Success(message)))
        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, null) to ResultWithError.Success(Unit),
            ),
        )

        val useCase = SendMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = message,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Message, SendMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }
    }
}
