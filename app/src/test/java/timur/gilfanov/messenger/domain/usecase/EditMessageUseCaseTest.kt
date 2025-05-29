package timur.gilfanov.messenger.domain.usecase

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
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule.CreationTimeCanNotChange
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule.EditWindow
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule.RecipientCanNotChange
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule.SenderIdCanNotChange
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError.CreationTimeChanged
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError.DeliveryStatusAlreadySet
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError.DeliveryStatusUpdateNotValid
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError.EditWindowExpired
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError.MessageIsNotValid
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError.RecipientChanged
import timur.gilfanov.messenger.domain.usecase.message.EditMessageError.SenderIdChanged
import timur.gilfanov.messenger.domain.usecase.message.EditMessageUseCase

class EditMessageUseCaseTest {

    private class RepositoryFake : Repository {
        private val chats = mutableMapOf<ChatId, Chat>()

        override suspend fun sendMessage(message: Message): Flow<Message> {
            error("Not yet implemented")
        }

        override suspend fun editMessage(message: Message): Flow<Message> {
            val updatedMessage = when (message) {
                is TextMessage -> message.copy(deliveryStatus = DeliveryStatus.Sent)
                else -> message
            }
            return flowOf(updatedMessage)
        }

        override suspend fun createChat(
            chat: Chat,
        ): ResultWithError<Chat, RepositoryCreateChatError> {
            chats[chat.id] = chat
            return Success(chat)
        }

        override suspend fun receiveChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, ReceiveChatUpdatesError>> {
            error("Not yet implemented")
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
            validateResults[Pair(currentStatus, newStatus)] ?: Success(Unit)
    }

    @Test
    fun `edit window expired rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 10.minutes
        val editWindowDuration = 5.minutes

        val participant = createParticipant(
            joinedAt = customTime - 20.minutes,
        )

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
            rules = persistentSetOf(EditWindow(editWindowDuration))
        }

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            chat = chat,
            message = editedMessage,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, EditMessageError>>(result)
            assertEquals(EditWindowExpired, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `creation time changed rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val originalCreatedAt = customTime - 2.minutes
        val newCreatedAt = customTime - 1.minutes

        val participant = createParticipant(
            joinedAt = customTime - 20.minutes,
        )

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
            rules = persistentSetOf(CreationTimeCanNotChange)
        }

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            chat = chat,
            message = editedMessage,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, EditMessageError>>(result)
            assertEquals(CreationTimeChanged, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `recipient changed rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = createParticipant(
            joinedAt = customTime - 20.minutes,
        )

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
            rules = persistentSetOf(RecipientCanNotChange)
        }

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            chat = chat,
            message = editedMessage,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, EditMessageError>>(result)
            assertEquals(RecipientChanged, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `sender id changed rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val originalParticipant = createParticipant(
            name = "User1",
            joinedAt = customTime - 20.minutes,
        )

        val newParticipant = createParticipant(
            name = "User2",
            joinedAt = customTime - 20.minutes,
        )

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
            rules = persistentSetOf(SenderIdCanNotChange)
        }

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            chat = chat,
            message = editedMessage,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, EditMessageError>>(result)
            assertEquals(SenderIdChanged, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `message validation failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = createParticipant(
            joinedAt = customTime - 20.minutes,
        )

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
        repository.createChat(chat)

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            chat = chat,
            message = editedMessage,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, EditMessageError>>(result)
            val error = result.error as MessageIsNotValid
            assertIs<TextValidationError>(error.reason)
            awaitComplete()
        }
    }

    @Test
    fun `delivery status already set`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = createParticipant(
            joinedAt = customTime - 20.minutes,
        )

        val chatId = ChatId(UUID.randomUUID())
        val deliveryStatus = Sending(50)

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
        repository.createChat(chat)

        val deliveryStatusValidator = DeliveryStatusValidatorFake()

        val useCase = EditMessageUseCase(
            chat = chat,
            message = editedMessage,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, EditMessageError>>(result)
            val error = result.error as DeliveryStatusAlreadySet
            assertEquals(deliveryStatus, error.status)
            awaitComplete()
        }
    }

    @Test
    fun `delivery status validation failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = createParticipant(
            joinedAt = customTime - 20.minutes,
        )

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
        repository.createChat(chat)

        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, DeliveryStatus.Sent) to Failure(validationError),
            ),
        )

        val useCase = EditMessageUseCase(
            chat = chat,
            message = editedMessage,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Failure<Message, EditMessageError>>(result)
            val error = result.error as DeliveryStatusUpdateNotValid
            assertEquals(validationError, error.error)
            awaitComplete()
        }
    }

    @Test
    fun `successful message edit`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = createParticipant(
            joinedAt = customTime - 20.minutes,
        )

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
                SenderIdCanNotChange,
                RecipientCanNotChange,
                CreationTimeCanNotChange,
                EditWindow(10.minutes),
            )
        }

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, DeliveryStatus.Sent) to Success(Unit),
            ),
        )

        val useCase = EditMessageUseCase(
            chat = chat,
            message = editedMessage,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, EditMessageError>>(result)
            val updatedMessage = result.data as TextMessage
            assertEquals(originalMessage.id, updatedMessage.id)
            assertEquals("Edited message", updatedMessage.text)
            assertEquals(DeliveryStatus.Sent, updatedMessage.deliveryStatus)
            awaitComplete()
        }
    }

    @Test
    fun `repository return error`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val participant = createParticipant(
            joinedAt = customTime - 20.minutes,
        )

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
        repository.createChat(chat)

        val deliveryStatusValidator = DeliveryStatusValidatorFake(
            validateResults = mapOf(
                Pair(null, DeliveryStatus.Sent) to Success(Unit),
            ),
        )

        val useCase = EditMessageUseCase(
            chat = chat,
            message = editedMessage,
            repository = repository,
            deliveryStatusValidator = deliveryStatusValidator,
            now = customTime,
        )

        useCase().test {
            val result = awaitItem()
            assertIs<Success<Message, EditMessageError>>(result)
            val updatedMessage = result.data as TextMessage
            assertEquals(DeliveryStatus.Sent, updatedMessage.deliveryStatus)
            awaitComplete()
        }
    }

    private fun createParticipant(
        id: ParticipantId = ParticipantId(UUID.randomUUID()),
        name: String = "User",
        joinedAt: Instant,
        pictureUrl: String? = null,
    ): Participant = Participant(
        id = id,
        name = name,
        joinedAt = joinedAt,
        pictureUrl = pictureUrl,
    )

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

        override fun validate(): ResultWithError<Unit, ValidationError> = Failure(validationError)
    }
}
