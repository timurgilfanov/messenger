package timur.gilfanov.messenger.domain.usecase.participant.message

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ValidationError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepository
import timur.gilfanov.messenger.domain.usecase.participant.ParticipantRepositoryNotImplemented

@Category(Unit::class)
class EditMessageUseCaseTest {

    private class RepositoryFake : ParticipantRepository by ParticipantRepositoryNotImplemented() {

        override suspend fun editMessage(message: Message): Flow<Message> {
            val updatedMessage = when (message) {
                is TextMessage -> message.copy(deliveryStatus = DeliveryStatus.Sent)
                else -> message
            }
            return flowOf(updatedMessage)
        }
    }

    private class DeliveryStatusValidatorFake(
        val validateResults: Map<
            Pair<DeliveryStatus?, DeliveryStatus?>,
            ResultWithError<Unit, DeliveryStatusValidationError>,
            > = mapOf(),
    ) : DeliveryStatusValidator {
        override fun validate(
            currentStatus: DeliveryStatus?,
            newStatus: DeliveryStatus?,
        ): ResultWithError<Unit, DeliveryStatusValidationError> =
            validateResults[Pair(currentStatus, newStatus)] ?: ResultWithError.Success(Unit)
    }

    @Test
    fun `edit window expired rule failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 10.minutes
        val editWindowDuration = 5.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val originalMessage = buildTextMessage {
            sender = participant
            createdAt = messageCreatedAt
            text = "Original message"
        }

        val editedMessage = buildTextMessage {
            id = originalMessage.id
            sender = participant
            recipient = originalMessage.recipient
            createdAt = messageCreatedAt
            text = "Edited message"
        }

        val chat = buildChat {
            id = originalMessage.recipient
            messages = persistentListOf(originalMessage)
            participants = persistentSetOf(participant)
            rules = persistentSetOf(EditMessageRule.EditWindow(editWindowDuration))
        }

        val repository = RepositoryFake()

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = editedMessage,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, EditMessageError>>(result)
            assertEquals(EditMessageError.EditWindowExpired, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `creation time changed rule failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val originalCreatedAt = customTime - 2.minutes
        val newCreatedAt = customTime - 1.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val originalMessage = buildTextMessage {
            sender = participant
            createdAt = originalCreatedAt
            text = "Original message"
        }

        val editedMessage = buildTextMessage {
            id = originalMessage.id
            sender = participant
            recipient = originalMessage.recipient
            createdAt = newCreatedAt
            text = "Edited message"
        }

        val chat = buildChat {
            id = originalMessage.recipient
            messages = persistentListOf(originalMessage)
            participants = persistentSetOf(participant)
            rules = persistentSetOf(EditMessageRule.CreationTimeCanNotChange)
        }

        val repository = RepositoryFake()

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = editedMessage,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, EditMessageError>>(result)
            assertEquals(EditMessageError.CreationTimeChanged, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `recipient changed rule failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val originalChatId = ChatId(UUID.randomUUID())
        val newChatId = ChatId(UUID.randomUUID())

        val originalMessage = buildTextMessage {
            sender = participant
            recipient = originalChatId
            createdAt = messageCreatedAt
            text = "Original message"
        }

        val editedMessage = buildTextMessage {
            id = originalMessage.id
            sender = participant
            recipient = newChatId
            createdAt = messageCreatedAt
            text = "Edited message"
        }

        val chat = buildChat {
            id = originalChatId
            messages = persistentListOf(originalMessage)
            participants = persistentSetOf(participant)
            rules = persistentSetOf(EditMessageRule.RecipientCanNotChange)
        }

        val repository = RepositoryFake()

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = editedMessage,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, EditMessageError>>(result)
            assertEquals(EditMessageError.RecipientChanged, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `sender id changed rule failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val originalParticipant = buildParticipant {
            name = "User1"
            joinedAt = customTime - 20.minutes
        }

        val newParticipant = buildParticipant {
            name = "User2"
            joinedAt = customTime - 20.minutes
        }

        val chatId = ChatId(UUID.randomUUID())

        val originalMessage = buildTextMessage {
            sender = originalParticipant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Original message"
        }

        val editedMessage = buildTextMessage {
            id = originalMessage.id
            sender = newParticipant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Edited message"
        }

        val chat = buildChat {
            id = chatId
            messages = persistentListOf(originalMessage)
            participants = persistentSetOf(originalParticipant, newParticipant)
            rules = persistentSetOf(EditMessageRule.SenderIdCanNotChange)
        }

        val repository = RepositoryFake()

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = editedMessage,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, EditMessageError>>(result)
            assertEquals(EditMessageError.SenderIdChanged, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `message validation failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val chatId = ChatId(UUID.randomUUID())
        val messageId = MessageId(UUID.randomUUID())

        val originalMessage = buildTextMessage {
            id = messageId
            sender = participant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Original message"
        }

        val validationError = TextValidationError.Empty
        val editedMessage = createInvalidMessage(
            id = messageId,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            validationError = validationError,
        )

        val chat = buildChat {
            id = chatId
            messages = persistentListOf(originalMessage)
            participants = persistentSetOf(participant)
        }

        val repository = RepositoryFake()

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = editedMessage,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, EditMessageError>>(result)
            val error = result.error as EditMessageError.MessageIsNotValid
            assertIs<TextValidationError>(error.reason)
            awaitComplete()
        }
    }

    @Test
    fun `delivery status already set`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val chatId = ChatId(UUID.randomUUID())
        val deliveryStatus = DeliveryStatus.Sending(50)

        val originalMessage = buildTextMessage {
            sender = participant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Original message"
        }

        val editedMessage = buildTextMessage {
            id = originalMessage.id
            sender = participant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Edited message"
            this.deliveryStatus = deliveryStatus
        }

        val chat = buildChat {
            id = chatId
            messages = persistentListOf(originalMessage)
            participants = persistentSetOf(participant)
        }

        val repository = RepositoryFake()

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = editedMessage,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, EditMessageError>>(result)
            val error = result.error as EditMessageError.DeliveryStatusAlreadySet
            assertEquals(deliveryStatus, error.status)
            awaitComplete()
        }
    }

    @Test
    fun `delivery status validation failure`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val chatId = ChatId(UUID.randomUUID())

        val originalMessage = buildTextMessage {
            sender = participant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Original message"
        }

        val editedMessage = buildTextMessage {
            id = originalMessage.id
            sender = participant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Edited message"
        }

        val chat = buildChat {
            id = chatId
            messages = persistentListOf(originalMessage)
            participants = persistentSetOf(participant)
        }

        val validationError = DeliveryStatusValidationError.CannotChangeFromRead

        val repository = RepositoryFake()

        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, DeliveryStatus.Sent) to ResultWithError.Failure(validationError),
            ),
        )

        val useCase = EditMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = editedMessage,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, EditMessageError>>(result)
            val error = result.error as EditMessageError.DeliveryStatusUpdateNotValid
            assertEquals(validationError, error.error)
            awaitComplete()
        }
    }

    @Test
    fun `successful message edit`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val chatId = ChatId(UUID.randomUUID())

        val originalMessage = buildTextMessage {
            sender = participant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Original message"
        }

        val editedMessage = buildTextMessage {
            id = originalMessage.id
            sender = participant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Edited message"
        }

        val chat = buildChat {
            id = chatId
            messages = persistentListOf(originalMessage)
            participants = persistentSetOf(participant)
            rules = persistentSetOf(
                EditMessageRule.SenderIdCanNotChange,
                EditMessageRule.RecipientCanNotChange,
                EditMessageRule.CreationTimeCanNotChange,
                EditMessageRule.EditWindow(10.minutes),
            )
        }

        val repository = RepositoryFake()

        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, DeliveryStatus.Sent) to ResultWithError.Success(Unit),
            ),
        )

        val useCase = EditMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = editedMessage,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Message, EditMessageError>>(result)
            val updatedMessage = result.data as TextMessage
            assertEquals(originalMessage.id, updatedMessage.id)
            assertEquals("Edited message", updatedMessage.text)
            assertEquals(DeliveryStatus.Sent, updatedMessage.deliveryStatus)
            awaitComplete()
        }
    }

    @Test
    fun `repository return error`() = runTest {
        val customTime = Instant.Companion.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = buildParticipant {
            joinedAt = customTime - 20.minutes
        }

        val chatId = ChatId(UUID.randomUUID())

        val originalMessage = buildTextMessage {
            sender = participant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Original message"
        }

        val editedMessage = buildTextMessage {
            id = originalMessage.id
            sender = participant
            recipient = chatId
            createdAt = messageCreatedAt
            text = "Edited message"
        }

        val chat = buildChat {
            id = chatId
            messages = persistentListOf(originalMessage)
            participants = persistentSetOf(participant)
        }

        val repository = RepositoryFake()

        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, DeliveryStatus.Sent) to ResultWithError.Success(Unit),
            ),
        )

        val useCase = EditMessageUseCase(
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
        )

        useCase(
            chat = chat,
            message = editedMessage,
            now = customTime,
        ).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Message, EditMessageError>>(result)
            val updatedMessage = result.data as TextMessage
            assertEquals(DeliveryStatus.Sent, updatedMessage.deliveryStatus)
            awaitComplete()
        }
    }

    private fun createInvalidMessage(
        id: MessageId,
        sender: Participant,
        recipient: ChatId,
        createdAt: Instant,
        validationError: ValidationError,
    ): Message = object : Message {
        override val id: MessageId = id
        override val parentId: MessageId? = null
        override val sender: Participant = sender
        override val recipient: ChatId = recipient
        override val createdAt: Instant = createdAt
        override val sentAt: Instant? = null
        override val deliveredAt: Instant? = null
        override val editedAt: Instant? = null
        override val deliveryStatus: DeliveryStatus? = null

        override fun validate(): ResultWithError<Unit, ValidationError> =
            ResultWithError.Failure(validationError)
    }
}
