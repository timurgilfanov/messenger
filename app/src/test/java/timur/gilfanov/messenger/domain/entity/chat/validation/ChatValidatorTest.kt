package timur.gilfanov.messenger.domain.entity.chat.validation

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.ResultWithError.Failure
import timur.gilfanov.messenger.domain.entity.ResultWithError.Success
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.EmptyName
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.NoParticipants
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.NonEmptyMessages
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.NonNullLastReadMessageId
import timur.gilfanov.messenger.domain.entity.chat.validation.ChatValidationError.NonZeroUnreadCount
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

@Category(timur.gilfanov.messenger.annotations.Unit::class)
class ChatValidatorTest {

    private val validator = ChatValidatorImpl()

    @Test
    fun validateValidParticipants() {
        val participant = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "Test User",
            joinedAt = Clock.System.now(),
            pictureUrl = null,
            onlineAt = null,
        )
        val participants = persistentSetOf(participant)

        val result = validator.validateParticipants(participants)

        assertTrue(result is Success)
    }

    @Test
    fun validateEmptyParticipants() {
        val participants = persistentSetOf<Participant>()

        val result = validator.validateParticipants(participants)

        assertTrue(result is Failure)
        assertEquals(NoParticipants, result.error)
    }

    @Test
    fun validateValidName() {
        val name = "Test Chat"

        val result = validator.validateName(name)

        assertTrue(result is Success)
    }

    @Test
    fun validateEmptyName() {
        val name = ""

        val result = validator.validateName(name)

        assertTrue(result is Failure)
        assertEquals(EmptyName, result.error)
    }

    @Test
    fun validateBlankName() {
        val name = "   "

        val result = validator.validateName(name)

        assertTrue(result is Failure)
        assertEquals(EmptyName, result.error)
    }

    @Test
    fun validateEmptyMessages() {
        val messages = persistentListOf<Message>()

        val result = validator.validateMessagesOnCreation(messages)

        assertTrue(result is Success)
    }

    @Test
    fun validateNonEmptyMessages() {
        val message = createMockMessage()
        val messages = persistentListOf(message)

        val result = validator.validateMessagesOnCreation(messages)

        assertTrue(result is Failure)
        assertEquals(NonEmptyMessages, result.error)
    }

    @Test
    fun validateZeroUnreadCount() {
        val unreadMessagesCount = 0

        val result = validator.validateUnreadMessagesCountOnCreation(unreadMessagesCount)

        assertTrue(result is Success)
    }

    @Test
    fun validateNonZeroUnreadCount() {
        val unreadMessagesCount = 5

        val result = validator.validateUnreadMessagesCountOnCreation(unreadMessagesCount)

        assertTrue(result is Failure)
        assertEquals(NonZeroUnreadCount, result.error)
    }

    @Test
    fun validateNullLastReadMessageId() {
        val lastReadMessageId: MessageId? = null

        val result = validator.validateLastReadMessageIdOnCreation(lastReadMessageId)

        assertTrue(result is Success)
    }

    @Test
    fun validateNonNullLastReadMessageId() {
        val lastReadMessageId = MessageId(UUID.randomUUID())

        val result = validator.validateLastReadMessageIdOnCreation(lastReadMessageId)

        assertTrue(result is Failure)
        assertEquals(NonNullLastReadMessageId, result.error)
    }

    @Test
    fun validateValidChat() {
        val chat = createValidChat()

        val result = validator.validate(chat)

        assertTrue(result is Success)
    }

    @Test
    fun validateChatWithEmptyParticipants() {
        val chat = createValidChat().copy(
            participants = persistentSetOf(),
        )

        val result = validator.validate(chat)

        assertTrue(result is Failure)
        assertEquals(NoParticipants, result.error)
    }

    @Test
    fun validateChatWithEmptyName() {
        val chat = createValidChat().copy(
            name = "",
        )

        val result = validator.validate(chat)

        assertTrue(result is Failure)
        assertEquals(EmptyName, result.error)
    }

    @Test
    fun validateValidChatOnCreation() {
        val chat = createValidChat()

        val result = validator.validateOnCreation(chat)

        assertTrue(result is Success)
    }

    @Test
    fun validateChatOnCreationWithNonEmptyMessages() {
        val message = createMockMessage()
        val chat = createValidChat().copy(
            messages = persistentListOf(message),
        )

        val result = validator.validateOnCreation(chat)

        assertTrue(result is Failure)
        assertEquals(NonEmptyMessages, result.error)
    }

    @Test
    fun validateChatOnCreationWithNonZeroUnreadCount() {
        val chat = createValidChat().copy(
            unreadMessagesCount = 5,
        )

        val result = validator.validateOnCreation(chat)

        assertTrue(result is Failure)
        assertEquals(NonZeroUnreadCount, result.error)
    }

    @Test
    fun validateChatOnCreationWithNonNullLastReadMessageId() {
        val chat = createValidChat().copy(
            lastReadMessageId = MessageId(UUID.randomUUID()),
        )

        val result = validator.validateOnCreation(chat)

        assertTrue(result is Failure)
        assertEquals(NonNullLastReadMessageId, result.error)
    }

    private fun createValidChat(): Chat {
        val participant = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "Test User",
            joinedAt = Clock.System.now(),
            pictureUrl = null,
            onlineAt = null,
        )
        return Chat(
            id = ChatId(UUID.randomUUID()),
            name = "Test Chat",
            pictureUrl = null,
            messages = persistentListOf(),
            participants = persistentSetOf(participant),
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
        )
    }

    private fun createMockMessage(): Message = object : Message {
        override val id = MessageId(UUID.randomUUID())
        override val parentId: MessageId? = null
        override val sender = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "Test User",
            joinedAt = Clock.System.now(),
            pictureUrl = null,
            onlineAt = null,
        )
        override val recipient = ChatId(UUID.randomUUID())
        override val createdAt = Clock.System.now()
        override val sentAt: Instant? = null
        override val deliveredAt: Instant? = null
        override val editedAt: Instant? = null
        override val deliveryStatus = null

        override fun validate(): ResultWithError<Unit, ChatValidationError> = Success(Unit)
    }
}
