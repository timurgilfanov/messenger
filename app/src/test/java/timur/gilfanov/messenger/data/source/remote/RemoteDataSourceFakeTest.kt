package timur.gilfanov.messenger.data.source.remote

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
class RemoteDataSourceFakeTest {

    private lateinit var remoteDataSource: RemoteDataSourceFake
    private lateinit var testChat: Chat
    private lateinit var testMessage: TextMessage
    private lateinit var testParticipant: Participant

    @Before
    fun setup() {
        remoteDataSource = RemoteDataSourceFake()

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
            deliveryStatus = DeliveryStatus.Sending(0),
        )

        testChat = Chat(
            id = chatId,
            participants = persistentSetOf(testParticipant),
            name = "Test Chat",
            pictureUrl = null,
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            messages = persistentListOf(),
        )
    }

    @Test
    fun `createChat should create chat when connected`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)

        // When
        val result = remoteDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Success<Chat, RemoteDataSourceError>>(result)
        assertEquals(testChat, result.data)

        // Verify chat is added to server
        remoteDataSource.subscribeToChats().test {
            val flowResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, RemoteDataSourceError>>(flowResult)
            assertEquals(1, flowResult.data.size)
            assertEquals(testChat.id, flowResult.data.first().id)
        }
    }

    @Test
    fun `createChat should return network error when disconnected`() = runTest {
        // Given
        remoteDataSource.setConnectionState(false)

        // When
        val result = remoteDataSource.createChat(testChat)

        // Then
        assertIs<ResultWithError.Failure<Chat, RemoteDataSourceError>>(result)
        assertEquals(RemoteDataSourceError.NetworkNotAvailable, result.error)
    }

    @Test
    fun `deleteChat should remove chat when connected`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)
        remoteDataSource.addChatToServer(testChat)

        // When
        val result = remoteDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Success<Unit, RemoteDataSourceError>>(result)

        // Verify chat is removed from server
        remoteDataSource.subscribeToChats().test {
            val flowResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, RemoteDataSourceError>>(flowResult)
            assertTrue(flowResult.data.isEmpty())
        }
    }

    @Test
    fun `deleteChat should return chat not found for non-existent chat`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)

        // When
        val result = remoteDataSource.deleteChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Unit, RemoteDataSourceError>>(result)
        assertEquals(RemoteDataSourceError.ChatNotFound, result.error)
    }

    @Test
    fun `joinChat should return chat when exists`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)
        remoteDataSource.addChatToServer(testChat)

        // When
        val result = remoteDataSource.joinChat(testChat.id, null)

        // Then
        assertIs<ResultWithError.Success<Chat, RemoteDataSourceError>>(result)
        assertEquals(testChat, result.data)
    }

    @Test
    fun `joinChat should return chat not found for non-existent chat`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)

        // When
        val result = remoteDataSource.joinChat(testChat.id, null)

        // Then
        assertIs<ResultWithError.Failure<Chat, RemoteDataSourceError>>(result)
        assertEquals(RemoteDataSourceError.ChatNotFound, result.error)
    }

    @Test
    fun `leaveChat should succeed for existing chat`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)
        remoteDataSource.addChatToServer(testChat)

        // When
        val result = remoteDataSource.leaveChat(testChat.id)

        // Then
        assertIs<ResultWithError.Success<Unit, RemoteDataSourceError>>(result)
    }

    @Test
    fun `leaveChat should return chat not found for non-existent chat`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)

        // When
        val result = remoteDataSource.leaveChat(testChat.id)

        // Then
        assertIs<ResultWithError.Failure<Unit, RemoteDataSourceError>>(result)
        assertEquals(RemoteDataSourceError.ChatNotFound, result.error)
    }

    @Test
    fun `subscribeToChats should emit chat previews when connected`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)
        remoteDataSource.addChatToServer(testChat)

        // When & Then
        remoteDataSource.subscribeToChats().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, RemoteDataSourceError>>(result)
            assertEquals(1, result.data.size)
            assertEquals(testChat.id, result.data.first().id)
        }
    }

    @Test
    fun `subscribeToChats should emit network error when disconnected`() = runTest {
        // Given
        remoteDataSource.setConnectionState(false)

        // When & Then
        remoteDataSource.subscribeToChats().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<List<ChatPreview>, RemoteDataSourceError>>(result)
            assertEquals(RemoteDataSourceError.NetworkNotAvailable, result.error)
        }
    }

    @Test
    fun `sendMessage should emit delivery status progression when connected`() = runTest {
        remoteDataSource.setConnectionState(true)
        remoteDataSource.addChatToServer(testChat)

        remoteDataSource.sendMessage(testMessage).test {
            val sendingStart = awaitItem()
            assertIs<ResultWithError.Success<TextMessage, *>>(sendingStart)
            assertIs<DeliveryStatus.Sending>(sendingStart.data.deliveryStatus)
            assertEquals(0, sendingStart.data.deliveryStatus.progress)

            val sendingMid = awaitItem()
            assertIs<ResultWithError.Success<TextMessage, *>>(sendingMid)
            assertIs<DeliveryStatus.Sending>(sendingMid.data.deliveryStatus)
            assertEquals(50, sendingMid.data.deliveryStatus.progress)

            val sendingComplete = awaitItem()
            assertIs<ResultWithError.Success<TextMessage, *>>(sendingComplete)
            assertIs<DeliveryStatus.Sending>(sendingComplete.data.deliveryStatus)
            assertEquals(100, sendingComplete.data.deliveryStatus.progress)

            val delivered = awaitItem()
            assertIs<ResultWithError.Success<TextMessage, *>>(delivered)
            assertIs<DeliveryStatus.Delivered>(delivered.data.deliveryStatus)

            val read = awaitItem()
            assertIs<ResultWithError.Success<TextMessage, *>>(read)
            assertIs<DeliveryStatus.Read>(read.data.deliveryStatus)

            awaitComplete()
        }
    }

    @Test
    fun `sendMessage should not emit when disconnected`() = runTest {
        remoteDataSource.setConnectionState(false)

        remoteDataSource.sendMessage(testMessage).test {
            awaitItem().apply {
                assertIs<ResultWithError.Failure<TextMessage, RemoteDataSourceError>>(this)
                assertEquals(RemoteDataSourceError.NetworkNotAvailable, this.error)
            }
            awaitComplete()
        }
    }

    @Test
    fun `editMessage should update message when connected`() = runTest {
        remoteDataSource.setConnectionState(true)
        val chatWithMessage = testChat.copy(messages = persistentListOf(testMessage))
        remoteDataSource.addChatToServer(chatWithMessage)
        val editedMessage = testMessage.copy(text = "Edited message")

        remoteDataSource.editMessage(editedMessage).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<TextMessage, RemoteDataSourceError>>(result)
            assertEquals(editedMessage.id, result.data.id)
            assertEquals("Edited message", result.data.text)
            awaitComplete()
        }
    }

    @Test
    fun `editMessage should return message not found for non-existent message`() = runTest {
        remoteDataSource.setConnectionState(true)
        remoteDataSource.addChatToServer(testChat)

        remoteDataSource.editMessage(testMessage).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<TextMessage, RemoteDataSourceError>>(result)
            assertEquals(RemoteDataSourceError.MessageNotFound, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `deleteMessage should remove message when connected`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)
        val chatWithMessage = testChat.copy(messages = persistentListOf(testMessage))
        remoteDataSource.addChatToServer(chatWithMessage)

        // When
        val result = remoteDataSource.deleteMessage(
            testMessage.id,
            DeleteMessageMode.FOR_EVERYONE,
        )

        // Then
        assertIs<ResultWithError.Success<Unit, RemoteDataSourceError>>(result)
    }

    @Test
    fun `deleteMessage should return message not found for non-existent message`() = runTest {
        // Given
        remoteDataSource.setConnectionState(true)
        remoteDataSource.addChatToServer(testChat)

        // When
        val result = remoteDataSource.deleteMessage(
            testMessage.id,
            DeleteMessageMode.FOR_EVERYONE,
        )

        // Then
        assertIs<ResultWithError.Failure<Unit, RemoteDataSourceError>>(result)
        assertEquals(RemoteDataSourceError.MessageNotFound, result.error)
    }

    @Test
    fun `isConnected should emit connection state changes`() = runTest {
        // When & Then
        remoteDataSource.isConnected().test {
            // Initial state should be true
            assertEquals(true, awaitItem())

            // Change connection state
            remoteDataSource.setConnectionState(false)
            assertEquals(false, awaitItem())

            // Change back to connected
            remoteDataSource.setConnectionState(true)
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `clearServerData should remove all chats`() = runTest {
        // Given
        remoteDataSource.addChatToServer(testChat)

        // When
        remoteDataSource.clearServerData()

        // Then
        remoteDataSource.subscribeToChats().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, RemoteDataSourceError>>(result)
            assertTrue(result.data.isEmpty())
        }
    }
}
