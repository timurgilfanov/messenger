package timur.gilfanov.messenger.domain.usecase

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.message.CanNotSendMessageError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Delivered
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sent
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.DeliveryStatusAlreadySet
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.DeliveryStatusUpdateNotValid
import timur.gilfanov.messenger.domain.usecase.CreateMessageError.MessageIsNotValid

class CreateMessageUseCaseTest {

    @Test
    fun `successful message creation`() = runTest {
        // Arrange
        val message = mockk<Message>()
        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null
        every { message.recipient.canSendMessage() } returns Success(Unit)
        every { deliveryStatusValidator.validate(null, any()) } returns Success(Unit)

        coEvery { repository.sendMessage(message) } returns flowOf(message)

        val useCase = CreateMessageUseCase(message, repository, deliveryStatusValidator)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result)
            assertEquals(message, result.data)
            awaitComplete()
        }

        verify { message.validate() }
        verify { message.recipient.canSendMessage() }
        verify { deliveryStatusValidator.validate(null, any()) }
    }

    @Test
    fun `unauthorized message creation`() = runTest {
        // Arrange
        val message = mockk<Message>()
        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()
        val canNotSendMessageError = mockk<CanNotSendMessageError>()

        every { message.recipient.canSendMessage() } returns Failure(canNotSendMessageError)

        val useCase = CreateMessageUseCase(message, repository, deliveryStatusValidator)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<Unauthorized>(result.error)
            assertEquals(canNotSendMessageError, result.error.reason)
            awaitComplete()
        }

        verify { message.recipient.canSendMessage() }
    }

    @Test
    fun `invalid message validation`() = runTest {
        // Arrange
        val message = mockk<Message>()
        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()
        val validationError = mockk<ValidationError>()

        every { message.recipient.canSendMessage() } returns Success(Unit)
        every { message.validate() } returns Failure(validationError)

        val useCase = CreateMessageUseCase(message, repository, deliveryStatusValidator)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<MessageIsNotValid>(result.error)
            assertEquals(validationError, result.error.reason)
            awaitComplete()
        }

        verify { message.recipient.canSendMessage() }
        verify { message.validate() }
    }

    @Test
    fun `message with delivery status already set`() = runTest {
        // Arrange
        val message = mockk<Message>()
        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()
        val deliveryStatus = mockk<DeliveryStatus>()

        every { message.recipient.canSendMessage() } returns Success(Unit)
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns deliveryStatus

        val useCase = CreateMessageUseCase(message, repository, deliveryStatusValidator)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<DeliveryStatusAlreadySet>(result.error)
            assertEquals(deliveryStatus, result.error.status)
            awaitComplete()
        }

        verify { message.recipient.canSendMessage() }
        verify { message.validate() }
        verify { message.deliveryStatus }
    }

    @Test
    fun `invalid delivery status update`() = runTest {
        // Arrange
        val message = mockk<Message>()
        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()
        val validationError = mockk<DeliveryStatusValidationError>()

        every { message.recipient.canSendMessage() } returns Success(Unit)
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        coEvery { repository.sendMessage(message) } returns flowOf(message)
        every { deliveryStatusValidator.validate(null, any()) } returns Failure(validationError)

        val useCase = CreateMessageUseCase(message, repository, deliveryStatusValidator)

        // Act & Assert
        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, CreateMessageError>>(result)
            assertIs<DeliveryStatusUpdateNotValid>(result.error)
            assertEquals(validationError, result.error.error)
            awaitComplete()
        }

        verify { message.recipient.canSendMessage() }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { deliveryStatusValidator.validate(null, any()) }
    }

    @Test
    fun `multiple delivery status updates`() = runTest {
        // Arrange
        val uuid = UUID.randomUUID()
        val message = mockk<Message>()
        val sending50 = mockk<Message>()
        val sending100 = mockk<Message>()
        val sentMessage = mockk<Message>()
        val deliveredMessage = mockk<Message>()

        every { message.id } returns uuid
        every { message.recipient.canSendMessage() } returns Success(Unit)
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        every { sending50.id } returns uuid
        every { sending50.deliveryStatus } returns Sending(50)

        every { sending100.id } returns uuid
        every { sending100.deliveryStatus } returns Sending(100)

        every { sentMessage.id } returns uuid
        every { sentMessage.deliveryStatus } returns Sent

        every { deliveredMessage.id } returns uuid
        every { deliveredMessage.deliveryStatus } returns Delivered

        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()
        every { deliveryStatusValidator.validate(null, Sending(50)) } returns Success(Unit)
        every { deliveryStatusValidator.validate(Sending(50), Sending(100)) } returns Success(Unit)
        every { deliveryStatusValidator.validate(Sending(100), Sent) } returns Success(Unit)
        every { deliveryStatusValidator.validate(Sent, Delivered) } returns Success(Unit)

        val repository = mockk<Repository>()
        coEvery { repository.sendMessage(message) } returns flow {
            emit(sending50)
            delay(1)
            emit(sending100)
            delay(1)
            emit(sentMessage)
            delay(1)
            emit(deliveredMessage)
        }

        val useCase = CreateMessageUseCase(message, repository, deliveryStatusValidator)

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
            assertIs<Success<Message, CreateMessageError>>(result3)
            assertEquals(sentMessage, result3.data)
            assertEquals(Sent, result3.data.deliveryStatus)

            val result4 = awaitItem()
            assertIs<Success<Message, CreateMessageError>>(result4)
            assertEquals(deliveredMessage, result4.data)
            assertEquals(Delivered, result4.data.deliveryStatus)

            awaitComplete()
        }

        verify { message.recipient.canSendMessage() }
        verify { message.validate() }
        verify { message.deliveryStatus }
        verify { deliveryStatusValidator.validate(null, Sending(50)) }
        verify { deliveryStatusValidator.validate(Sending(50), Sending(100)) }
        verify { deliveryStatusValidator.validate(Sending(100), Sent) }
        verify { deliveryStatusValidator.validate(Sent, Delivered) }
    }

    @Test
    fun `repository throws exception`() = runTest {
        // Arrange
        val message = mockk<Message>()
        val repository = mockk<Repository>()
        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()
        val exception = RuntimeException("Network error")

        every { message.recipient.canSendMessage() } returns Success(Unit)
        every { message.validate() } returns Success(Unit)
        every { message.deliveryStatus } returns null

        coEvery { repository.sendMessage(message) } throws exception

        val useCase = CreateMessageUseCase(message, repository, deliveryStatusValidator)

        // Act & Assert
        useCase().test {
            assertEquals(exception, awaitError())
        }

        verify { message.recipient.canSendMessage() }
        verify { message.validate() }
        verify { message.deliveryStatus }
    }
}
