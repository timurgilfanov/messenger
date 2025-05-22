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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.Rule.CanNotWriteAfterJoining
import timur.gilfanov.messenger.domain.entity.chat.Rule.Debounce
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sent
import timur.gilfanov.messenger.domain.entity.message.Message
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
        // Arrange
        val now = Instant.fromEpochMilliseconds(1000000)
        val joinedAt = now - 1.minutes
        val waitDuration = 5.minutes
        val remainingTime = waitDuration - (now - joinedAt)

        val senderId = UUID.randomUUID()
        val participant = mockk<Participant>()
        every { participant.id } returns senderId
        every { participant.joinedAt } returns joinedAt

        val message = mockk<Message>()
        every { message.sender } returns participant

        val chat = mockk<Chat>()
        every { chat.participants } returns setOf(participant)
        every { chat.rules } returns setOf(CanNotWriteAfterJoining(waitDuration))

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = now,
        )

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<WaitAfterJoining>(result.error)
            assertEquals(remainingTime, result.error.duration)
            awaitComplete()
        }

        verify { chat.participants }
        verify { chat.rules }
        verify { participant.id }
        verify { participant.joinedAt }
        verify { message.sender }
    }

    @Test
    fun `test debounce rule failure`() = runTest {
        // Arrange
        val now = Instant.fromEpochMilliseconds(1000000)
        val lastMessageTime = now - 10.seconds
        val debounceDelay = 30.seconds
        val remainingTime = debounceDelay - (now - lastMessageTime)

        val senderId = UUID.randomUUID()
        val participant = mockk<Participant>()
        every { participant.id } returns senderId

        val lastMessage = mockk<Message>()
        every { lastMessage.sender.id } returns senderId
        every { lastMessage.createdAt } returns lastMessageTime

        val message = mockk<Message>()
        every { message.sender.id } returns senderId

        val chat = mockk<Chat>()
        every { chat.rules } returns setOf(Debounce(debounceDelay))
        every { chat.messages } returns listOf(lastMessage)

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = now,
        )

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<WaitDebounce>(result.error)
            assertEquals(remainingTime, result.error.duration)
            awaitComplete()
        }

        verify { chat.rules }
        verify { chat.messages }
        verify { message.sender.id }
        verify { lastMessage.sender.id }
        verify { lastMessage.createdAt }
    }

    @Test
    fun `test multiple rules success`() = runTest {
        // Arrange
        val now = Instant.fromEpochMilliseconds(1000000)
        val joinedAt = now - 10.minutes
        val lastMessageTime = now - 1.minutes

        val senderId = UUID.randomUUID()
        val participant = mockk<Participant>()
        every { participant.id } returns senderId
        every { participant.joinedAt } returns joinedAt

        val lastMessage = mockk<Message>()
        every { lastMessage.sender.id } returns senderId
        every { lastMessage.createdAt } returns lastMessageTime

        val messageSender = mockk<Participant>()
        every { messageSender.id } returns senderId

        val message = mockk<Message>()
        every { message.sender } returns messageSender
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        val chat = mockk<Chat>()
        every { chat.participants } returns setOf(participant)
        every { chat.rules } returns setOf(
            CanNotWriteAfterJoining(5.minutes),
            Debounce(30.seconds),
        )
        every { chat.messages } returns listOf(lastMessage)

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        every { deliveryStatusValidator.validate(null, any()) } returns Success(Unit)
        coEvery { repository.sendMessage(message) } returns flowOf(message)

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = now,
        )

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }

        verify { chat.participants }
        verify { chat.rules }
        verify { chat.messages }
        verify { participant.joinedAt }
        verify { message.sender }
        verify { messageSender.id }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { lastMessage.sender.id }
        verify { lastMessage.createdAt }
        verify { deliveryStatusValidator.validate(null, any()) }
    }

    @Test
    fun `test with custom time`() = runTest {
        // Arrange
        val customTime = Instant.fromEpochMilliseconds(2000000)
        val joinedAt = customTime - 10.minutes

        val senderId = UUID.randomUUID()
        val participant = mockk<Participant>()
        every { participant.id } returns senderId
        every { participant.joinedAt } returns joinedAt

        val message = mockk<Message>()
        every { message.sender.id } returns senderId
        every { message.sender } returns participant
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        val chat = mockk<Chat>()
        every { chat.participants } returns setOf(participant)
        every { chat.rules } returns setOf(CanNotWriteAfterJoining(5.minutes))
        every { chat.messages } returns emptyList()

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

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }

        verify { chat.participants }
        verify { chat.rules }
        verify { participant.id }
        verify { participant.joinedAt }
        verify { message.sender }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { deliveryStatusValidator.validate(null, any()) }
    }

    @Test
    fun `test with empty rules`() = runTest {
        // Arrange
        val now = Clock.System.now()

        val message = mockk<Message>()
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        val chat = mockk<Chat>()
        every { chat.rules } returns emptySet()

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        every { deliveryStatusValidator.validate(null, any()) } returns Success(Unit)
        coEvery { repository.sendMessage(message) } returns flowOf(message)

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = now,
        )

        // Act & Assert
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
    fun `delivery status validation succeed then failed`() = runTest {
        // Arrange
        val uuid = UUID.randomUUID()
        val now = Clock.System.now()

        val message = mockk<Message>()
        val sending50 = mockk<Message>()
        val sending100 = mockk<Message>()
        val sentMessage = mockk<Message>()

        every { message.id } returns uuid
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        every { sending50.id } returns uuid
        every { sending50.deliveryStatus } returns Sending(50)

        every { sending100.id } returns uuid
        every { sending100.deliveryStatus } returns Sending(100)

        every { sentMessage.id } returns uuid
        every { sentMessage.deliveryStatus } returns Sent

        val chat = mockk<Chat>()
        every { chat.rules } returns emptySet()

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
            now = now,
        )

        // Act & Assert
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
        // Arrange
        val uuid = UUID.randomUUID()
        val now = Clock.System.now()

        val message = mockk<Message>()
        val sending50 = mockk<Message>()

        every { message.id } returns uuid
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        every { sending50.id } returns uuid
        every { sending50.deliveryStatus } returns Sending(50)

        val chat = mockk<Chat>()
        every { chat.rules } returns emptySet()

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
            now = now,
        )

        // Act & Assert
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
        // Arrange
        val now = Clock.System.now()
        val validationError = mockk<ValidationError>()

        val message = mockk<Message>()
        every { message.validate() } returns Failure(validationError)

        val chat = mockk<Chat>()
        every { chat.rules } returns emptySet()

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = now,
        )

        // Act & Assert
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
        // Arrange
        val now = Clock.System.now()
        val existingStatus = mockk<DeliveryStatus>()

        val message = mockk<Message>()
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns existingStatus

        val chat = mockk<Chat>()
        every { chat.rules } returns emptySet()

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = now,
        )

        // Act & Assert
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
        // Arrange
        val now = Clock.System.now()
        val exception = RuntimeException("Network error")

        val message = mockk<Message>()
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        val chat = mockk<Chat>()
        every { chat.rules } returns emptySet()

        val repository = mockk<Repository>()
        coEvery { repository.sendMessage(message) } throws exception

        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = now,
        )

        // Act & Assert
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
        // Arrange
        val now = Clock.System.now()
        val senderId = UUID.randomUUID()
        val otherParticipantId = UUID.randomUUID()

        val otherParticipant = mockk<Participant>()
        every { otherParticipant.id } returns otherParticipantId

        val messageSender = mockk<Participant>()
        every { messageSender.id } returns senderId

        val message = mockk<Message>()
        every { message.sender } returns messageSender

        val chat = mockk<Chat>()
        every { chat.rules } returns setOf(CanNotWriteAfterJoining(5.minutes))
        every { chat.participants } returns setOf(otherParticipant)

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = now,
        )

        // Act & Assert
        useCase().test {
            val error = awaitError()
            assert(error is NoSuchElementException)
        }

        verify { chat.rules }
        verify { chat.participants }
        verify { message.sender }
        verify { messageSender.id }
        verify { otherParticipant.id }
    }

    @Test
    fun `last message not found`() = runTest {
        // Arrange
        val now = Clock.System.now()
        val senderId = UUID.randomUUID()

        val messageSender = mockk<Participant>()
        every { messageSender.id } returns senderId

        val message = mockk<Message>()
        every { message.sender } returns messageSender
        every { message.sender.id } returns senderId
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        val chat = mockk<Chat>()
        every { chat.rules } returns setOf(Debounce(30.seconds))
        every { chat.messages } returns emptyList()

        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        every { deliveryStatusValidator.validate(null, any()) } returns Success(Unit)
        coEvery { repository.sendMessage(message) } returns flowOf(message)

        val useCase = CreateMessageUseCase(
            chat = chat,
            message = message,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = now,
        )

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }

        verify { chat.rules }
        verify { chat.messages }
        verify { message.sender.id }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { deliveryStatusValidator.validate(null, any()) }
    }
}
