package timur.gilfanov.messenger.entity

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import org.junit.Test
import timur.gilfanov.messenger.entity.model.chat.Chat
import timur.gilfanov.messenger.entity.model.chat.ChatUser
import timur.gilfanov.messenger.entity.model.chat.isGroupChat
import timur.gilfanov.messenger.entity.model.message.DeliveryStatus
import timur.gilfanov.messenger.entity.model.message.Message

class ChatUnitTest {

    @Test
    fun createChatInstance() {
        val chatId = UUID.randomUUID()
        val messages = mutableListOf<Message>()
        val users = mutableListOf<ChatUser>()

        val chat = Chat(chatId, messages, users)

        assertEquals(chatId, chat.id)
        assertEquals(messages, chat.messages)
        assertEquals(users, chat.users)
    }

    @Test
    fun detectGroupChatCorrectly() {
        val chatId = UUID.randomUUID()
        val messages = mutableListOf<Message>()
        val users = mutableListOf(
            ChatUser(UUID.randomUUID(), "User1", null),
            ChatUser(UUID.randomUUID(), "User2", null),
            ChatUser(UUID.randomUUID(), "User3", null),
        )

        val chat = Chat(chatId, messages, users)

        assertTrue(chat.isGroupChat)
    }

    @Test
    fun detectNonGroupChatCorrectly() {
        val chatId = UUID.randomUUID()
        val messages = mutableListOf<Message>()
        val users = mutableListOf(
            ChatUser(UUID.randomUUID(), "User1", null),
            ChatUser(UUID.randomUUID(), "User2", null),
        )

        val chat = Chat(chatId, messages, users)

        assertFalse(chat.isGroupChat)
    }

    @Test
    fun addMessageToChat() {
        val chatId = UUID.randomUUID()
        val messages = mutableListOf<Message>()
        val users = mutableListOf<ChatUser>()

        val chat = Chat(chatId, messages, users)
        val initialSize = chat.messages.size

        val sender = ChatUser(UUID.randomUUID(), "Sender", null)
        val recipient = ChatUser(UUID.randomUUID(), "Recipient", null)

        val mockMessage = object : Message {
            override val id = UUID.randomUUID()
            override val sender = sender
            override val recipient = recipient
            override val sentAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
            override var deliveryStatus: DeliveryStatus = DeliveryStatus.Sent
        }

        chat.messages.add(mockMessage)

        assertEquals(initialSize + 1, chat.messages.size)
        assertEquals(mockMessage, chat.messages.last())
    }

    @Test
    fun addUserToChat() {
        val chatId = UUID.randomUUID()
        val messages = mutableListOf<Message>()
        val users = mutableListOf<ChatUser>()

        val chat = Chat(chatId, messages, users)
        val initialSize = chat.users.size

        val newUser = ChatUser(UUID.randomUUID(), "New User", null)
        chat.users.add(newUser)

        assertEquals(initialSize + 1, chat.users.size)
        assertEquals(newUser, chat.users.last())
    }

    @Test
    fun emptyUsersListHandling() {
        val chatId = UUID.randomUUID()
        val messages = mutableListOf<Message>()
        val users = mutableListOf<ChatUser>()

        val chat = Chat(chatId, messages, users)

        assertEquals(0, chat.users.size)
        assertFalse(chat.isGroupChat)
    }

    @Test
    fun emptyMessagesListHandling() {
        val chatId = UUID.randomUUID()
        val messages = mutableListOf<Message>()
        val users = mutableListOf<ChatUser>()

        val chat = Chat(chatId, messages, users)

        assertEquals(0, chat.messages.size)
    }

    @Test
    fun dataClassEqualityCheck() {
        val chatId = UUID.randomUUID()
        val messages = mutableListOf<Message>()
        val users = mutableListOf<ChatUser>()

        val chat1 = Chat(chatId, messages, users)
        val chat2 = Chat(chatId, messages, users)

        assertEquals(chat1, chat2)
        assertEquals(chat1.hashCode(), chat2.hashCode())

        val differentChat = Chat(UUID.randomUUID(), messages, users)
        assertNotEquals(chat1, differentChat)
    }
}
