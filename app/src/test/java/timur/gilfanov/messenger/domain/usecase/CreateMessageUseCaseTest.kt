package timur.gilfanov.messenger.domain.usecase

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule.CanNotWriteAfterJoining
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule.Debounce
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sent
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.DeliveryStatusAlreadySet
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.DeliveryStatusUpdateNotValid
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.MessageIsNotValid
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.WaitAfterJoining
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.WaitDebounce

class CreateMessageUseCaseTest {

    @Test
    fun `test joining rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val joinedAt = customTime - 1.minutes
        val waitDuration = 5.minutes
        val remainingTime = waitDuration - (customTime - joinedAt)

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = joinedAt,
            pictureUrl = null,
        )

        val message = mockk<Message> {
            every { sender } returns participant
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf(CanNotWriteAfterJoining(waitDuration))
        }

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        verify { chat.participants }
        verify { chat.rules }
        verify { message.sender }
    }

    @Test
    fun `test debounce rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val lastMessageTime = customTime - 10.seconds
        val debounceDelay = 30.seconds
        val remainingTime = debounceDelay - (customTime - lastMessageTime)

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val lastMessage = mockk<Message> {
            every { sender } returns participant
            every { createdAt } returns lastMessageTime
        }

        val message = mockk<Message> {
            every { sender } returns participant
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf(Debounce(debounceDelay))
            every { messages } returns persistentListOf(lastMessage)
        }

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        verify { chat.rules }
        verify { chat.messages }
        verify { message.sender }
        verify { lastMessage.sender }
        verify { lastMessage.createdAt }
    }

    @Test
    fun `test multiple rules success`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val joinedAt = customTime - 10.minutes
        val lastMessageTime = customTime - 1.minutes

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = joinedAt,
            pictureUrl = null,
        )

        val lastMessage = mockk<Message> {
            every { sender } returns participant
            every { createdAt } returns lastMessageTime
        }

        val message = mockk<Message> {
            every { sender } returns participant
            every { validate() } returns Success(Unit)
            every { deliveryStatus } returns null
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf(
                CanNotWriteAfterJoining(5.minutes),
                Debounce(30.seconds),
            )
            every { messages } returns persistentListOf(lastMessage)
        }

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        every { deliveryStatusValidator.validate(null, any()) } returns Success(Unit)
        coEvery { repository.sendMessage(message) } returns flowOf(message)

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

        verify { chat.participants }
        verify { chat.rules }
        verify { chat.messages }
        verify { message.sender }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { lastMessage.createdAt }
        verify { deliveryStatusValidator.validate(null, any()) }
    }

    @Test
    fun `test with custom time`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val joinedAt = customTime - 10.minutes

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = joinedAt,
            pictureUrl = null,
        )

        val message = mockk<Message> {
            every { sender } returns participant
            every { validate() } returns Success(Unit)
            every { deliveryStatus } returns null
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf(CanNotWriteAfterJoining(5.minutes))
            every { messages } returns persistentListOf()
        }

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        every { deliveryStatusValidator.validate(null, any()) } returns Success(Unit)
        coEvery { repository.sendMessage(message) } returns flowOf(message)

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

        verify { chat.participants }
        verify { chat.rules }
        verify { message.sender }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { deliveryStatusValidator.validate(null, any()) }
    }

    @Test
    fun `test with empty rules`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val message = mockk<Message> {
            every { sender } returns participant
            every { validate() } returns Success(Unit)
            every { deliveryStatus } returns null
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf()
            every { messages } returns persistentListOf()
        }

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        every { deliveryStatusValidator.validate(null, any()) } returns Success(Unit)
        coEvery { repository.sendMessage(message) } returns flowOf(message)

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

        verify { chat.rules }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { deliveryStatusValidator.validate(null, any()) }
    }

    @Test
    @Suppress("LongMethod")
    fun `delivery status validation succeed then failed`() = runTest {
        val messageId = MessageId(UUID.randomUUID())
        val customTime = Instant.fromEpochMilliseconds(2000000)

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val message = mockk<Message> {
            every { id } returns messageId
            every { sender } returns participant
            every { validate() } returns Success(Unit)
            every { deliveryStatus } returns null
        }

        val sending50 = mockk<Message> {
            every { id } returns messageId
            every { deliveryStatus } returns Sending(50)
        }

        val sending100 = mockk<Message> {
            every { id } returns messageId
            every { deliveryStatus } returns Sending(100)
        }

        val sentMessage = mockk<Message> {
            every { id } returns messageId
            every { deliveryStatus } returns Sent
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf()
            every { messages } returns persistentListOf()
        }

        val validationError = mockk<DeliveryStatusValidationError>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()
        every { deliveryStatusValidator.validate(null, Sending(50)) } returns Success(Unit)
        every { deliveryStatusValidator.validate(Sending(50), Sending(100)) } returns Success(Unit)
        every { deliveryStatusValidator.validate(Sending(100), Sent) } returns
            Failure(validationError)

        val repository = mockk<Repository>()
        coEvery { repository.sendMessage(message) } returns flow {
            emit(sending50)
            delay(1)
            emit(sending100)
            delay(1)
            emit(sentMessage)
        }

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
            assertEquals(sending100, result2.data)
            assertEquals(Sending(100), result2.data.deliveryStatus)

            val result3 = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result3)
            assertIs<DeliveryStatusUpdateNotValid>(result3.error)
            assertEquals(validationError, result3.error.error)

            awaitComplete()
        }

        verify { chat.rules }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { deliveryStatusValidator.validate(null, Sending(50)) }
        verify { deliveryStatusValidator.validate(Sending(50), Sending(100)) }
        verify { deliveryStatusValidator.validate(Sending(100), Sent) }
    }

    @Test
    fun `delivery status validation failed on first update`() = runTest {
        val messageId = MessageId(UUID.randomUUID())
        val customTime = Instant.fromEpochMilliseconds(2000000)

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val message = mockk<Message> {
            every { id } returns messageId
            every { sender } returns participant
            every { validate() } returns Success(Unit)
            every { deliveryStatus } returns null
        }

        val sending50 = mockk<Message> {
            every { id } returns messageId
            every { deliveryStatus } returns Sending(50)
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf()
            every { messages } returns persistentListOf()
        }

        val validationError = mockk<DeliveryStatusValidationError>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()
        every { deliveryStatusValidator.validate(null, Sending(50)) } returns
            Failure(validationError)

        val repository = mockk<Repository>()
        coEvery { repository.sendMessage(message) } returns flow {
            emit(sending50)
        }

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

        verify { chat.rules }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { deliveryStatusValidator.validate(null, Sending(50)) }
    }

    @Test
    fun `message validation failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val validationError = mockk<ValidationError>()

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val message = mockk<Message> {
            every { sender } returns participant
            every { validate() } returns Failure(validationError)
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf()
        }

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        verify { chat.rules }
        verify { message.validate() }
    }

    @Test
    fun `delivery status already set`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val existingStatus = mockk<DeliveryStatus>()

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val message = mockk<Message> {
            every { sender } returns participant
            every { validate() } returns Success(Unit)
            every { deliveryStatus } returns existingStatus
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf()
        }

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        verify { chat.rules }
        verify { message.validate() }
        verify { message.deliveryStatus }
    }

    @Test
    fun `repository throws exception`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val exception = RuntimeException("Network error")

        val senderId = ParticipantId(UUID.randomUUID())
        val participant = Participant(
            id = senderId,
            name = "",
            joinedAt = customTime - 10.minutes,
            pictureUrl = null,
        )

        val message = mockk<Message> {
            every { sender } returns participant
            every { validate() } returns Success(Unit)
            every { deliveryStatus } returns null
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf()
        }

        val repository = mockk<Repository>()
        coEvery { repository.sendMessage(message) } throws exception

        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        verify { chat.rules }
        verify { message.validate() }
        verify { message.deliveryStatus }
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

        val message = mockk<Message> {
            every { sender } returns messageSender
        }

        val chat = mockk<Chat> {
            every { rules } returns persistentSetOf(CanNotWriteAfterJoining(5.minutes))
            every { participants } returns persistentSetOf(otherParticipant)
        }

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        verify { chat.rules }
        verify { chat.participants }
        verify { message.sender }
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

        val message = mockk<Message> {
            every { sender } returns participant
            every { validate() } returns Success(Unit)
            every { deliveryStatus } returns null
        }

        val chat = mockk<Chat> {
            every { participants } returns persistentSetOf(participant)
            every { rules } returns persistentSetOf(Debounce(30.seconds))
            every { messages } returns persistentListOf()
        }

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        every { deliveryStatusValidator.validate(null, any()) } returns Success(Unit)
        coEvery { repository.sendMessage(message) } returns flowOf(message)

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

        verify { chat.rules }
        verify { chat.messages }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { deliveryStatusValidator.validate(null, any()) }
    }
}
