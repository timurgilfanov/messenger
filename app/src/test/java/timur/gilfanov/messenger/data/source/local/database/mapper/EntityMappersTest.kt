package timur.gilfanov.messenger.data.source.local.database.mapper

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.annotations.Unit
import timur.gilfanov.messenger.data.source.local.database.entity.ChatWithParticipantsAndMessages
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.createChatParticipantCrossRefs
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.toChat
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.toChatEntity
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.toChatPreview
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.toMessage
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.toMessageEntity
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.toParticipant
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers.toParticipantEntity
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage

@Category(Unit::class)
class EntityMappersTest {

    private val testParticipant = buildParticipant {
        id = ParticipantId(UUID.randomUUID())
        name = "Test User"
        pictureUrl = "https://example.com/user.jpg"
        joinedAt = Instant.fromEpochMilliseconds(1000)
        onlineAt = Instant.fromEpochMilliseconds(2000)
    }
    private val testMessage = buildTextMessage {
        id = MessageId(UUID.randomUUID())
        text = "Test message"
        sender = testParticipant
        recipient = ChatId(UUID.randomUUID())
        createdAt = Instant.fromEpochMilliseconds(3000)
    }
    private val testChat = buildChat {
        id = ChatId(UUID.randomUUID())
        name = "Test Chat"
        pictureUrl = "https://example.com/chat.jpg"
        participants = persistentSetOf(testParticipant)
        messages = persistentListOf(testMessage)
        unreadMessagesCount = 5
    }

    @Test
    fun `participant to entity and back preserves all data`() {
        // Given
        val participant = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "John Doe",
            pictureUrl = "https://example.com/pic.jpg",
            joinedAt = Instant.fromEpochMilliseconds(1000),
            onlineAt = Instant.fromEpochMilliseconds(2000),
        )

        // When
        val entity = participant.toParticipantEntity()
        val crossRef = with(EntityMappers) {
            participant.toChatParticipantCrossRef("test-chat-id")
        }
        val restored = entity.toParticipant(crossRef)

        // Then
        assertEquals(participant, restored)
        assertEquals(participant.id.id.toString(), entity.id)
        assertEquals(participant.name, entity.name)
        assertEquals(participant.pictureUrl, entity.pictureUrl)
        assertEquals(participant.onlineAt, entity.onlineAt)
        // Chat-specific properties come from crossRef
        assertEquals(participant.joinedAt, crossRef.joinedAt)
        assertEquals(participant.isAdmin, crossRef.isAdmin)
        assertEquals(participant.isModerator, crossRef.isModerator)
    }

    @Test
    fun `participant with null fields maps correctly`() {
        // Given
        val participant = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "Jane Doe",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(1000),
            onlineAt = null,
        )

        // When
        val entity = participant.toParticipantEntity()
        val crossRef = with(EntityMappers) {
            participant.toChatParticipantCrossRef("test-chat-id")
        }
        val restored = entity.toParticipant(crossRef)

        // Then
        assertEquals(participant, restored)
        assertNull(entity.pictureUrl)
        assertNull(entity.onlineAt)
    }

    @Test
    fun `text message to entity and back preserves all data`() {
        // Given
        val message = TextMessage(
            id = MessageId(UUID.randomUUID()),
            text = "Hello, World!",
            parentId = MessageId(UUID.randomUUID()),
            sender = testParticipant,
            recipient = ChatId(UUID.randomUUID()),
            createdAt = Instant.fromEpochMilliseconds(3000),
            deliveryStatus = DeliveryStatus.Sending(50),
        )

        // When
        val entity = message.toMessageEntity()
        val crossRef = with(EntityMappers) {
            testParticipant.toChatParticipantCrossRef(message.recipient.id.toString())
        }
        val restored = entity.toMessage(
            listOf(testParticipant.toParticipantEntity()),
            listOf(crossRef),
        )

        // Then
        assertIs<TextMessage>(restored)
        assertEquals(message.id, restored.id)
        assertEquals(message.text, restored.text)
        assertEquals(message.parentId, restored.parentId)
        assertEquals(message.sender, restored.sender)
        assertEquals(message.recipient, restored.recipient)
        assertEquals(message.createdAt, restored.createdAt)
        assertEquals(message.deliveryStatus, restored.deliveryStatus)
    }

    @Test
    fun `message with null delivery status maps correctly`() {
        // Given
        val message = TextMessage(
            id = MessageId(UUID.randomUUID()),
            text = "Test message",
            parentId = null,
            sender = testParticipant,
            recipient = ChatId(UUID.randomUUID()),
            createdAt = Instant.fromEpochMilliseconds(4000),
            deliveryStatus = null,
        )

        // When
        val entity = message.toMessageEntity()
        val crossRef = with(EntityMappers) {
            testParticipant.toChatParticipantCrossRef(message.recipient.id.toString())
        }
        val restored = entity.toMessage(
            listOf(testParticipant.toParticipantEntity()),
            listOf(crossRef),
        )

        // Then
        assertIs<TextMessage>(restored)
        assertEquals(message.id, restored.id)
        assertEquals(message.text, restored.text)
        assertNull(restored.parentId)
        assertEquals(message.sender, restored.sender)
        assertEquals(message.recipient, restored.recipient)
        assertEquals(message.createdAt, restored.createdAt)
        assertNull(restored.deliveryStatus)
    }

    @Test
    fun `chat to entity preserves basic data`() {
        // Given
        val chat = Chat(
            id = ChatId(UUID.randomUUID()),
            participants = persistentSetOf(testParticipant),
            name = "Test Chat",
            pictureUrl = "https://example.com/chat.jpg",
            rules = persistentSetOf(
                CreateMessageRule.Debounce(5.seconds),
                EditMessageRule.EditWindow(2.minutes),
                DeleteMessageRule.SenderCanDeleteOwn,
                DeleteChatRule.OnlyAdminCanDelete,
            ),
            unreadMessagesCount = 5,
            lastReadMessageId = MessageId(UUID.randomUUID()),
            messages = persistentListOf(testMessage),
        )

        // When
        val entity = chat.toChatEntity()

        // Then
        assertEquals(chat.id.id.toString(), entity.id)
        assertEquals(chat.name, entity.name)
        assertEquals(chat.pictureUrl, entity.pictureUrl)
        assertEquals(chat.unreadMessagesCount, entity.unreadMessagesCount)
        assertEquals(chat.lastReadMessageId?.id?.toString(), entity.lastReadMessageId)
        assertNotNull(entity.rules) // JSON string should not be null
    }

    @Test
    fun `ChatWithParticipantsAndMessages to Chat preserves all data`() {
        // Given
        val chatEntity = testChat.toChatEntity()
        val participantEntities = testChat.participants.map { it.toParticipantEntity() }
        val messageEntities = testChat.messages.map { it.toMessageEntity() }
        val participantCrossRefs = testChat.participants.map { participant ->
            with(EntityMappers) { participant.toChatParticipantCrossRef(testChat.id.id.toString()) }
        }

        val chatWithRelations = ChatWithParticipantsAndMessages(
            chat = chatEntity,
            participants = participantEntities,
            participantCrossRefs = participantCrossRefs,
            messages = messageEntities,
        )

        // When
        val restoredChat = chatWithRelations.toChat()

        // Then
        assertEquals(testChat.id, restoredChat.id)
        assertEquals(testChat.name, restoredChat.name)
        assertEquals(testChat.pictureUrl, restoredChat.pictureUrl)
        assertEquals(testChat.unreadMessagesCount, restoredChat.unreadMessagesCount)
        assertEquals(testChat.lastReadMessageId, restoredChat.lastReadMessageId)
        assertEquals(testChat.participants.size, restoredChat.participants.size)
        assertEquals(testChat.messages.size, restoredChat.messages.size)
    }

    @Test
    fun `ChatWithParticipantsAndMessages to ChatPreview creates correct preview`() {
        // Given
        val chatEntity = testChat.toChatEntity()
        val participantEntities = testChat.participants.map { it.toParticipantEntity() }
        val messageEntities = testChat.messages.map { it.toMessageEntity() }
        val participantCrossRefs = testChat.participants.map { participant ->
            with(EntityMappers) { participant.toChatParticipantCrossRef(testChat.id.id.toString()) }
        }

        val chatWithRelations = ChatWithParticipantsAndMessages(
            chat = chatEntity,
            participants = participantEntities,
            participantCrossRefs = participantCrossRefs,
            messages = messageEntities,
        )

        // When
        val preview = chatWithRelations.toChatPreview()

        // Then
        assertEquals(testChat.id, preview.id)
        assertEquals(testChat.name, preview.name)
        assertEquals(testChat.pictureUrl, preview.pictureUrl)
        assertEquals(testChat.unreadMessagesCount, preview.unreadMessagesCount)
        assertNotNull(preview.lastMessage) // Should have last message since chat has messages
    }

    @Test
    fun `empty chat with no messages creates preview with null lastMessage`() {
        // Given
        val chatEntity = testChat.copy(messages = persistentListOf()).toChatEntity()
        val participantEntities = testChat.participants.map { it.toParticipantEntity() }
        val participantCrossRefs = testChat.participants.map { participant ->
            with(EntityMappers) { participant.toChatParticipantCrossRef(testChat.id.id.toString()) }
        }

        val chatWithRelations = ChatWithParticipantsAndMessages(
            chat = chatEntity,
            participants = participantEntities,
            participantCrossRefs = participantCrossRefs,
            messages = emptyList(),
        )

        // When
        val preview = chatWithRelations.toChatPreview()

        // Then
        assertNull(preview.lastMessage)
    }

    @Test
    fun `delivery status serialization and deserialization`() {
        // Test all delivery status types
        val statuses = listOf(
            DeliveryStatus.Sending(75),
            DeliveryStatus.Sent,
            DeliveryStatus.Delivered,
            DeliveryStatus.Read,
            // Note: DeliveryStatus.Failed is handled separately due to DeliveryError complexity
        )

        statuses.forEach { status ->
            val dto = status.toDto()
            val restored = dto.toDomain()
            assertEquals(status, restored, "Failed to restore $status")
        }
    }

    @Test
    fun `rule serialization and deserialization`() {
        // Test various rule types
        val rules = listOf(
            CreateMessageRule.CanNotWriteAfterJoining(10.minutes),
            CreateMessageRule.Debounce(2.seconds),
            EditMessageRule.EditWindow(5.minutes),
            EditMessageRule.SenderIdCanNotChange,
            EditMessageRule.RecipientCanNotChange,
            EditMessageRule.CreationTimeCanNotChange,
            DeleteMessageRule.DeleteWindow(15.minutes),
            DeleteMessageRule.SenderCanDeleteOwn,
            DeleteMessageRule.AdminCanDeleteAny,
            DeleteMessageRule.ModeratorCanDeleteAny,
            DeleteMessageRule.NoDeleteAfterDelivered,
            DeleteMessageRule.DeleteForEveryoneWindow(30.minutes),
            DeleteChatRule.OnlyAdminCanDelete,
        )

        rules.forEach { rule ->
            val dto = rule.toDto()
            val restored = dto.toDomain()
            assertEquals(rule, restored, "Failed to restore $rule")
        }
    }

    @Test
    fun `createChatParticipantCrossRefs creates correct cross references`() {
        // Given
        val chatId = ChatId(UUID.randomUUID())
        val participants = setOf(
            buildParticipant { id = ParticipantId(UUID.randomUUID()) },
            buildParticipant { id = ParticipantId(UUID.randomUUID()) },
            buildParticipant { id = ParticipantId(UUID.randomUUID()) },
        )

        // When
        val crossRefs = createChatParticipantCrossRefs(chatId, participants)

        // Then
        assertEquals(participants.size, crossRefs.size)
        crossRefs.forEach { crossRef ->
            assertEquals(chatId.id.toString(), crossRef.chatId)
            assert(
                participants.any { participant ->
                    participant.id.id.toString() == crossRef.participantId
                },
            )
        }
    }
}
