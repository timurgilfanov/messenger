package timur.gilfanov.messenger.domain.usecase

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import timur.gilfanov.messenger.data.repository.RepositoryFake
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
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus.Sending
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidationError
import timur.gilfanov.messenger.domain.entity.message.validation.DeliveryStatusValidator
import timur.gilfanov.messenger.domain.entity.message.validation.TextValidationError
import timur.gilfanov.messenger.domain.usecase.EditMessageError.CreationTimeChanged
import timur.gilfanov.messenger.domain.usecase.EditMessageError.DeliveryStatusAlreadySet
import timur.gilfanov.messenger.domain.usecase.EditMessageError.DeliveryStatusUpdateNotValid
import timur.gilfanov.messenger.domain.usecase.EditMessageError.EditWindowExpired
import timur.gilfanov.messenger.domain.usecase.EditMessageError.MessageIsNotValid
import timur.gilfanov.messenger.domain.usecase.EditMessageError.RecipientChanged
import timur.gilfanov.messenger.domain.usecase.EditMessageError.SenderIdChanged

class EditMessageUseCaseTest {

    @Test
    fun `edit window expired rule failure`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 10.minutes
        val editWindowDuration = 5.minutes

        val messageUuid = UUID.randomUUID()
        val messageId = MessageId(messageUuid)
        val senderId = ParticipantId(UUID.randomUUID())
        val chatId = ChatId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "User",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val originalMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Original message",
        )

        val editedMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Edited message",
        )

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(originalMessage),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(EditWindow(editWindowDuration)),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        val messageUuid = UUID.randomUUID()
        val messageId = MessageId(messageUuid)
        val senderId = ParticipantId(UUID.randomUUID())
        val chatId = ChatId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "User",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val originalMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = originalCreatedAt,
            text = "Original message",
        )

        val editedMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = newCreatedAt,
            text = "Edited message",
        )

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(originalMessage),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(CreationTimeCanNotChange),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        val messageUuid = UUID.randomUUID()
        val messageId = MessageId(messageUuid)
        val senderId = ParticipantId(UUID.randomUUID())
        val originalChatId = ChatId(UUID.randomUUID())
        val newChatId = ChatId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "User",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val originalMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = originalChatId,
            createdAt = messageCreatedAt,
            text = "Original message",
        )

        val editedMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = newChatId,
            createdAt = messageCreatedAt,
            text = "Edited message",
        )

        val chat = Chat(
            id = originalChatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(originalMessage),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(RecipientCanNotChange),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        val messageUuid = UUID.randomUUID()
        val messageId = MessageId(messageUuid)
        val originalSenderId = ParticipantId(UUID.randomUUID())
        val newSenderId = ParticipantId(UUID.randomUUID())
        val chatId = ChatId(UUID.randomUUID())

        val originalParticipant = Participant(
            id = originalSenderId,
            name = "User1",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val newParticipant = Participant(
            id = newSenderId,
            name = "User2",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val originalMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = originalParticipant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Original message",
        )

        val editedMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = newParticipant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Edited message",
        )

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(originalMessage),
            participants = persistentSetOf(originalParticipant, newParticipant),
            rules = persistentSetOf(SenderIdCanNotChange),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        val messageUuid = UUID.randomUUID()
        val messageId = MessageId(messageUuid)
        val senderId = ParticipantId(UUID.randomUUID())
        val chatId = ChatId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "User",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val originalMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Original message",
        )

        val validationError = mockk<TextValidationError>()
        val editedMessage = mockk<Message> {
            every { id } returns messageId
            every { sender } returns participant
            every { recipient } returns chatId
            every { createdAt } returns messageCreatedAt
            every { validate() } returns Failure(validationError)
        }

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(originalMessage),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        val messageUuid = UUID.randomUUID()
        val messageId = MessageId(messageUuid)
        val senderId = ParticipantId(UUID.randomUUID())
        val chatId = ChatId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "User",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val deliveryStatus = Sending(50)

        val originalMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Original message",
        )

        val editedMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Edited message",
            deliveryStatus = deliveryStatus,
        )

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(originalMessage),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = mockk<DeliveryStatusValidator>()

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

        val messageUuid = UUID.randomUUID()
        val messageId = MessageId(messageUuid)
        val senderId = ParticipantId(UUID.randomUUID())
        val chatId = ChatId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "User",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val originalMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Original message",
        )

        val editedMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Edited message",
        )

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(originalMessage),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val validationError = mockk<DeliveryStatusValidationError>()

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = mockk<DeliveryStatusValidator> {
            every { validate(null, DeliveryStatus.Sent) } returns Failure(validationError)
        }

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

        val messageUuid = UUID.randomUUID()
        val messageId = MessageId(messageUuid)
        val senderId = ParticipantId(UUID.randomUUID())
        val chatId = ChatId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "User",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val originalMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Original message",
        )

        val editedMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Edited message",
        )

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(originalMessage),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(
                SenderIdCanNotChange,
                RecipientCanNotChange,
                CreationTimeCanNotChange,
                EditWindow(10.minutes),
            ),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = mockk<DeliveryStatusValidator> {
            every { validate(null, DeliveryStatus.Sent) } returns Success(Unit)
        }

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
            assertEquals(messageId, updatedMessage.id)
            assertEquals("Edited message", updatedMessage.text)
            assertEquals(DeliveryStatus.Sent, updatedMessage.deliveryStatus)
            awaitComplete()
        }
    }

    @Test
    fun `repository return error`() = runTest {
        val customTime = Instant.fromEpochMilliseconds(1000000)
        val messageCreatedAt = customTime - 2.minutes

        val messageUuid = UUID.randomUUID()
        val messageId = MessageId(messageUuid)
        val senderId = ParticipantId(UUID.randomUUID())
        val chatId = ChatId(UUID.randomUUID())

        val participant = Participant(
            id = senderId,
            name = "User",
            joinedAt = customTime - 20.minutes,
            pictureUrl = null,
        )

        val originalMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Original message",
        )

        val editedMessage = TextMessage(
            id = messageId,
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = messageCreatedAt,
            text = "Edited message",
        )

        val chat = Chat(
            id = chatId,
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(originalMessage),
            participants = persistentSetOf(participant),
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )

        val repository = RepositoryFake()
        repository.createChat(chat)

        val deliveryStatusValidator = mockk<DeliveryStatusValidator> {
            every { validate(null, DeliveryStatus.Sent) } returns Success(Unit)
        }

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
}
