package timur.gilfanov.messenger.data.source.local

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.annotations.Unit
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode

@Category(Unit::class)
class LocalDataSourceFakeTest {

    private lateinit var localDataSource: LocalDataSourceFake
    private lateinit var testChat: Chat
    private lateinit var testMessage: TextMessage
    private lateinit var testParticipant: Participant

    @Before
    fun setup() {
        localDataSource = LocalDataSourceFake()

        testParticipant = Participant(
            id = ParticipantId(UUID.randomUUID()),
            name = "Test User",
            pictureUrl = null,
            joinedAt = Clock.System.now(),
            onlineAt = Clock.System.now(),
        )

        val chatId = ChatId(UUID.randomUUID())
        testMessage = TextMessage(
            id = MessageId(UUID.randomUUID()),
            text = "Test message",
            parentId = null,
            sender = testParticipant,
            recipient = chatId,
            createdAt = Clock.System.now(),
            deliveryStatus = DeliveryStatus.Read,
        )

        testChat = Chat(
            id = chatId,
            participants = persistentSetOf(testParticipant),
            name = "Test Chat",
            pictureUrl = null,
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            messages = persistentListOf(testMessage),
        )
    }

    @Test
    fun `insertChat should store chat and emit in flow`() = runTest {
        // When
        val result = localDataSource.insertChat(testChat)

        // Then
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(result)
        assertEquals(testChat, result.data)

        // Verify flow emits the chat
        localDataSource.flowChatList().test {
            val flowResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, LocalDataSourceError>>(flowResult)
            assertEquals(1, flowResult.data.size)
            assertEquals(testChat.id, flowResult.data.first().id)
            assertEquals(testMessage, flowResult.data.first().lastMessage)
        }
    }

    @Test
    fun `updateChat should update existing chat`() = runTest {
        // Given
        localDataSource.insertChat(testChat)
        val updatedChat = testChat.copy(name = "Updated Chat")

        // When
        val result = localDataSource.updateChat(updatedChat)

        // Then
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(result)
        assertEquals(updatedChat, result.data)

        // Verify the chat is updated in flow
        localDataSource.flowChatList().test {
            val flowResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, LocalDataSourceError>>(flowResult)
            assertEquals("Updated Chat", flowResult.data.first().name)
        }
    }

    @Test
    fun `updateChat should return error for non-existent chat`() = runTest {
        // When
        val result = localDataSource.updateChat(testChat)

        // Then
        assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
        assertEquals(LocalDataSourceError.ChatNotFound, result.error)
    }

    @Test
    fun `deleteChat should remove chat`() = runTest {
        // Given
        localDataSource.insertChat(testChat)

        // When
        val result = localDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify chat is removed from flow
        localDataSource.flowChatList().test {
            val flowResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, LocalDataSourceError>>(flowResult)
            assertTrue(flowResult.data.isEmpty())
        }
    }

    @Test
    fun `deleteChat should return error for non-existent chat`() = runTest {
        // When
        val result = localDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Unit, LocalDataSourceError>>(result)
        assertEquals(LocalDataSourceError.ChatNotFound, result.error)
    }

    @Test
    fun `getChat should return existing chat`() = runTest {
        // Given
        localDataSource.insertChat(testChat)

        // When
        val result = localDataSource.getChat(testChat.id)

        // Then
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(result)
        assertEquals(testChat, result.data)
    }

    @Test
    fun `getChat should return error for non-existent chat`() = runTest {
        // When
        val result = localDataSource.getChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
        assertEquals(LocalDataSourceError.ChatNotFound, result.error)
    }

    @Test
    fun `flowChatUpdates should emit chat updates`() = runTest {
        // Given
        localDataSource.insertChat(testChat)

        localDataSource.flowChatUpdates(testChat.id).test {
            // Initial emission
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(initialResult)
            assertEquals(testChat, initialResult.data)

            // Update the chat
            val updatedChat = testChat.copy(name = "Updated Chat")
            localDataSource.updateChat(updatedChat)

            // Should emit updated chat
            val updatedResult = awaitItem()
            assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(updatedResult)
            assertEquals("Updated Chat", updatedResult.data.name)
        }
    }

    @Test
    fun `insertMessage should add message to chat`() = runTest {
        // Given
        localDataSource.insertChat(testChat)
        val newMessage = TextMessage(
            id = MessageId(UUID.randomUUID()),
            text = "New message",
            parentId = null,
            sender = testParticipant,
            recipient = testChat.id,
            createdAt = Clock.System.now(),
            deliveryStatus = DeliveryStatus.Delivered,
        )

        // When
        val result = localDataSource.insertMessage(newMessage)

        // Then
        assertIs<ResultWithError.Success<TextMessage, LocalDataSourceError>>(result)
        assertEquals(newMessage, result.data)

        // Verify message is added to chat
        val chatResult = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(chatResult)
        assertEquals(2, chatResult.data.messages.size)
        assertTrue(chatResult.data.messages.contains(newMessage))
    }

    @Test
    fun `insertMessage should return error for non-existent chat`() = runTest {
        // When
        val result = localDataSource.insertMessage(testMessage)

        // Then
        assertIs<ResultWithError.Failure<TextMessage, LocalDataSourceError>>(result)
        assertEquals(LocalDataSourceError.ChatNotFound, result.error)
    }

    @Test
    fun `updateMessage should update existing message`() = runTest {
        // Given
        localDataSource.insertChat(testChat)
        val updatedMessage = testMessage.copy(text = "Updated message")

        // When
        val result = localDataSource.updateMessage(updatedMessage)

        // Then
        assertIs<ResultWithError.Success<TextMessage, LocalDataSourceError>>(result)
        assertEquals(updatedMessage, result.data)

        // Verify message is updated in chat
        val chatResult = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(chatResult)
        assertEquals("Updated message", (chatResult.data.messages.first() as TextMessage).text)
    }

    @Test
    fun `deleteMessage should remove message from chat`() = runTest {
        // Given
        localDataSource.insertChat(testChat)

        // When
        val result = localDataSource.deleteMessage(
            testMessage.id,
            DeleteMessageMode.FOR_EVERYONE,
        )

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify message is removed from chat
        val chatResult = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(chatResult)
        assertTrue(chatResult.data.messages.isEmpty())
    }

    @Test
    fun `deleteMessage should return error for non-existent message`() = runTest {
        // When
        val result = localDataSource.deleteMessage(
            MessageId(UUID.randomUUID()),
            DeleteMessageMode.FOR_EVERYONE,
        )

        // Then
        assertIs<ResultWithError.Failure<Unit, LocalDataSourceError>>(result)
        assertEquals(LocalDataSourceError.MessageNotFound, result.error)
    }

    @Test
    fun `getMessage should return existing message`() = runTest {
        // Given
        localDataSource.insertChat(testChat)

        // When
        val result = localDataSource.getMessage(testMessage.id)

        // Then
        assertIs<ResultWithError.Success<TextMessage, LocalDataSourceError>>(result)
        assertEquals(testMessage, result.data)
    }

    @Test
    fun `getMessage should return error for non-existent message`() = runTest {
        // When
        val result = localDataSource.getMessage(MessageId(UUID.randomUUID()))

        // Then
        assertIs<ResultWithError.Failure<TextMessage, LocalDataSourceError>>(result)
        assertEquals(LocalDataSourceError.MessageNotFound, result.error)
    }

    @Test
    fun `clearAllData should remove all chats`() = runTest {
        // Given
        localDataSource.insertChat(testChat)

        // When
        val result = localDataSource.clearAllData()

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify all data is cleared
        localDataSource.flowChatList().test {
            val flowResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, LocalDataSourceError>>(flowResult)
            assertTrue(flowResult.data.isEmpty())
        }
    }
}
