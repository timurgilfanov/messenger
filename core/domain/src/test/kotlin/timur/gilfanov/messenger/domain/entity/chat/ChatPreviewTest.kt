package timur.gilfanov.messenger.domain.entity.chat

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage

@Category(Unit::class)
class ChatPreviewTest {

    @Test
    fun `fromChat should create ChatPreview with last message when chat has messages`() {
        // Given
        val chatId = ChatId(UUID.randomUUID())
        val participant = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "Test User",
            pictureUrl = null,
            joinedAt = Clock.System.now(),
            onlineAt = Clock.System.now(),
        )
        val messageId1 = MessageId(UUID.randomUUID())
        val messageId2 = MessageId(UUID.randomUUID())
        val message1 = TextMessage(
            id = messageId1,
            text = "First message",
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = Clock.System.now(),
            deliveryStatus = DeliveryStatus.Read,
        )
        val message2 = TextMessage(
            id = messageId2,
            text = "Second message",
            parentId = null,
            sender = participant,
            recipient = chatId,
            createdAt = Clock.System.now(),
            deliveryStatus = DeliveryStatus.Delivered,
        )

        val chat = Chat(
            id = chatId,
            participants = persistentSetOf(participant),
            name = "Test Chat",
            pictureUrl = "http://example.com/pic.jpg",
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>(),
            unreadMessagesCount = 1,
            lastReadMessageId = messageId1,
            messages = persistentListOf(message1, message2),
        )

        // When
        val chatPreview = ChatPreview.fromChat(chat)

        // Then
        assertEquals(chatId, chatPreview.id)
        assertEquals(persistentSetOf(participant), chatPreview.participants)
        assertEquals("Test Chat", chatPreview.name)
        assertEquals("http://example.com/pic.jpg", chatPreview.pictureUrl)
        assertEquals(persistentSetOf(), chatPreview.rules)
        assertEquals(1, chatPreview.unreadMessagesCount)
        assertEquals(messageId1, chatPreview.lastReadMessageId)
        assertEquals(message2, chatPreview.lastMessage) // Should be the last message
        assertEquals(message2.createdAt, chatPreview.lastActivityAt)
    }

    @Test
    fun `fromChat should create ChatPreview with null last message when chat has no messages`() {
        // Given
        val chatId = ChatId(UUID.randomUUID())
        val participant = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "Test User",
            pictureUrl = null,
            joinedAt = Clock.System.now(),
            onlineAt = Clock.System.now(),
        )

        val chat = Chat(
            id = chatId,
            participants = persistentSetOf(participant),
            name = "Empty Chat",
            pictureUrl = null,
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            messages = persistentListOf(),
        )

        // When
        val chatPreview = ChatPreview.fromChat(chat)

        // Then
        assertEquals(chatId, chatPreview.id)
        assertEquals(persistentSetOf(participant), chatPreview.participants)
        assertEquals("Empty Chat", chatPreview.name)
        assertNull(chatPreview.pictureUrl)
        assertEquals(persistentSetOf(), chatPreview.rules)
        assertEquals(0, chatPreview.unreadMessagesCount)
        assertNull(chatPreview.lastReadMessageId)
        assertNull(chatPreview.lastMessage)
        assertNull(chatPreview.lastActivityAt)
    }

    @Test
    fun `fromChat should preserve all chat properties correctly`() {
        // Given
        val chatId = ChatId(UUID.randomUUID())
        val participant1 = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "User 1",
            pictureUrl = null,
            joinedAt = Clock.System.now(),
            onlineAt = Clock.System.now(),
        )
        val participant2 = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "User 2",
            pictureUrl = null,
            joinedAt = Clock.System.now(),
            onlineAt = Clock.System.now(),
        )
        val rule = CreateMessageRule.Debounce(kotlin.time.Duration.parse("1s"))
        val messageId = MessageId(UUID.randomUUID())

        val chat = Chat(
            id = chatId,
            participants = persistentSetOf(participant1, participant2),
            name = "Multi-user Chat",
            pictureUrl = "http://example.com/group.jpg",
            rules = persistentSetOf(rule),
            unreadMessagesCount = 5,
            lastReadMessageId = messageId,
            messages = persistentListOf(),
        )

        // When
        val chatPreview = ChatPreview.fromChat(chat)

        // Then
        assertEquals(chatId, chatPreview.id)
        assertEquals(persistentSetOf(participant1, participant2), chatPreview.participants)
        assertEquals("Multi-user Chat", chatPreview.name)
        assertEquals("http://example.com/group.jpg", chatPreview.pictureUrl)
        assertEquals(persistentSetOf(rule), chatPreview.rules)
        assertEquals(5, chatPreview.unreadMessagesCount)
        assertEquals(messageId, chatPreview.lastReadMessageId)
    }
}
