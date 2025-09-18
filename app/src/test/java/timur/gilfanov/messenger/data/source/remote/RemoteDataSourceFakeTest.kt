package timur.gilfanov.messenger.data.source.remote

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode

@Category(Unit::class)
class RemoteDataSourceFakeTest {

    private lateinit var remoteDataSource: RemoteDataSourceFake
    private lateinit var testChat: Chat
    private lateinit var testMessage: TextMessage
    private lateinit var testParticipant: Participant

    companion object {
        private val TEST_PARTICIPANT_ID = ParticipantId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
        )
        private val TEST_CHAT_ID = ChatId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        )
        private val TEST_MESSAGE_ID = MessageId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
        )
        private val TEST_MESSAGE_ID_2 = MessageId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
        )
        private val TEST_MESSAGE_ID_3 = MessageId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440004"),
        )
        private val TEST_CHAT_ID_NON_EXISTENT = ChatId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440005"),
        )
        private val TEST_MESSAGE_ID_NON_EXISTENT = MessageId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440006"),
        )

        private fun testChatIdWithSuffix(suffix: Int): ChatId {
            require(suffix in 0..1000) { "Suffix must be between 0 and 1000" }
            val base = "550e8400-e29b-41d4-a716-44665544"
            val hex = suffix.toString(16).padStart(4, '0')
            return ChatId(UUID.fromString("$base$hex"))
        }

        private val TEST_TIMESTAMP = Instant.fromEpochMilliseconds(1640995200000)
    }

    @Before
    fun setup() {
        remoteDataSource = RemoteDataSourceFake()

        testParticipant = Participant(
            id = TEST_PARTICIPANT_ID,
            name = "Test User",
            pictureUrl = null,
            joinedAt = TEST_TIMESTAMP,
            onlineAt = TEST_TIMESTAMP,
        )

        testMessage = TextMessage(
            id = TEST_MESSAGE_ID,
            text = "Test message",
            parentId = null,
            sender = testParticipant,
            recipient = TEST_CHAT_ID,
            createdAt = TEST_TIMESTAMP,
            deliveryStatus = DeliveryStatus.Sending(0),
        )

        testChat = Chat(
            id = TEST_CHAT_ID,
            participants = persistentSetOf(testParticipant),
            name = "Test Chat",
            pictureUrl = null,
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>(),
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
        remoteDataSource.chatPreviews().test {
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
        remoteDataSource.chatPreviews().test {
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
        remoteDataSource.chatPreviews().test {
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
        remoteDataSource.chatPreviews().test {
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
    fun `clearServerData should remove all chats`() = runTest {
        // Given
        remoteDataSource.addChatToServer(testChat)

        // When
        remoteDataSource.clearServerData()

        // Then
        remoteDataSource.chatPreviews().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, RemoteDataSourceError>>(result)
            assertTrue(result.data.isEmpty())
        }
    }

    @Test
    fun `markMessagesAsRead should mark messages as read and update unread count`() = runTest {
        // Given
        val chatWithMessages = testChat.copy(
            messages = persistentListOf(testMessage),
            unreadMessagesCount = 1,
            lastReadMessageId = null,
        )
        remoteDataSource.addChatToServer(chatWithMessages)

        // When
        val result = remoteDataSource.markMessagesAsRead(chatWithMessages.id, testMessage.id)

        // Then
        assertIs<ResultWithError.Success<Unit, RemoteDataSourceError>>(result)

        // Verify the chat was updated
        remoteDataSource.chatPreviews().test {
            val chatsResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, RemoteDataSourceError>>(chatsResult)
            val chatPreview = chatsResult.data.first { it.id == chatWithMessages.id }
            assertEquals(0, chatPreview.unreadMessagesCount)
            assertEquals(testMessage.id, chatPreview.lastReadMessageId)
        }
    }

    @Test
    fun `markMessagesAsRead should generate delta for synchronization`() = runTest {
        // Given
        val chatWithMessages = testChat.copy(
            messages = persistentListOf(testMessage),
            unreadMessagesCount = 1,
            lastReadMessageId = null,
        )
        remoteDataSource.addChatToServer(chatWithMessages)

        // Get initial sync point
        val initialResult = remoteDataSource.chatsDeltaUpdates(null).first()
        assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(initialResult)
        val syncPoint = initialResult.data.toTimestamp

        // When
        remoteDataSource.markMessagesAsRead(chatWithMessages.id, testMessage.id)

        // Then - should generate a delta after the sync point
        val incrementalResult = remoteDataSource.chatsDeltaUpdates(syncPoint).first()
        assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(incrementalResult)
        val incrementalDelta = incrementalResult.data
        assertTrue(incrementalDelta.changes.isNotEmpty())
        val chatDelta = incrementalDelta.changes.first()
        assertIs<ChatUpdatedDelta>(chatDelta)
        assertEquals(chatWithMessages.id, chatDelta.chatId)
        assertEquals(0, chatDelta.chatMetadata.unreadMessagesCount)
        assertEquals(testMessage.id, chatDelta.chatMetadata.lastReadMessageId)
    }

    @Test
    fun `markMessagesAsRead with multiple messages should calculate correct unread count`() =
        runTest {
            // Given
            val message1 = testMessage
            val message2 = testMessage.copy(id = TEST_MESSAGE_ID_2)
            val message3 = testMessage.copy(id = TEST_MESSAGE_ID_3)

            val chatWithMessages = testChat.copy(
                messages = persistentListOf(message1, message2, message3),
                unreadMessagesCount = 3,
                lastReadMessageId = null,
            )
            remoteDataSource.addChatToServer(chatWithMessages)

            // When - mark up to the second message
            val result = remoteDataSource.markMessagesAsRead(chatWithMessages.id, message2.id)

            // Then
            assertIs<ResultWithError.Success<Unit, RemoteDataSourceError>>(result)

            // Should have 1 unread message remaining (message3)
            remoteDataSource.chatPreviews().test {
                val chatsResult = awaitItem()
                assertIs<ResultWithError.Success<List<ChatPreview>, RemoteDataSourceError>>(
                    chatsResult,
                )
                val chatPreview = chatsResult.data.first { it.id == chatWithMessages.id }
                assertEquals(1, chatPreview.unreadMessagesCount)
                assertEquals(message2.id, chatPreview.lastReadMessageId)
            }
        }

    @Test
    fun `markMessagesAsRead with non-existent message should keep original unread count`() =
        runTest {
            // Given
            val chatWithMessages = testChat.copy(
                messages = persistentListOf(testMessage),
                unreadMessagesCount = 1,
                lastReadMessageId = null,
            )
            remoteDataSource.addChatToServer(chatWithMessages)
            val nonExistentMessageId = TEST_MESSAGE_ID_NON_EXISTENT

            // When
            val result = remoteDataSource.markMessagesAsRead(
                chatWithMessages.id,
                nonExistentMessageId,
            )

            // Then
            assertIs<ResultWithError.Success<Unit, RemoteDataSourceError>>(result)

            // Should keep original unread count since message doesn't exist
            remoteDataSource.chatPreviews().test {
                val chatsResult = awaitItem()
                assertIs<ResultWithError.Success<List<ChatPreview>, RemoteDataSourceError>>(
                    chatsResult,
                )
                val chatPreview = chatsResult.data.first { it.id == chatWithMessages.id }
                assertEquals(1, chatPreview.unreadMessagesCount)
                assertEquals(nonExistentMessageId, chatPreview.lastReadMessageId)
            }
        }

    @Test
    fun `markMessagesAsRead on non-existent chat should return ChatNotFound`() = runTest {
        // Given
        val nonExistentChatId = TEST_CHAT_ID_NON_EXISTENT
        val messageId = TEST_MESSAGE_ID_NON_EXISTENT

        // When
        val result = remoteDataSource.markMessagesAsRead(nonExistentChatId, messageId)

        // Then
        assertIs<ResultWithError.Failure<Unit, RemoteDataSourceError>>(result)
        assertEquals(RemoteDataSourceError.ChatNotFound, result.error)

        // No chats should exist
        remoteDataSource.chatPreviews().test {
            val chatsResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, RemoteDataSourceError>>(
                chatsResult,
            )
            assertTrue(chatsResult.data.isEmpty())
        }
    }

    // Regression tests for sync race condition fixes
    @Test
    fun `clearServerData should use deterministic timestamps that advance forward`() = runTest {
        // Given - Add a chat to establish server timestamp
        val testChatBefore = testChat.copy(
            id = TEST_CHAT_ID_NON_EXISTENT, // Use different ID to avoid conflicts
        )
        remoteDataSource.addChatToServer(testChatBefore)

        // Get the timestamp before clearing
        val deltaBeforeClear = remoteDataSource.chatsDeltaUpdates(null).first()
        assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(deltaBeforeClear)
        val timestampBeforeClear = deltaBeforeClear.data.toTimestamp

        // When - Clear server data
        (remoteDataSource as RemoteDebugDataSource).clearServerData()

        // Add a new chat after clearing
        val testChatAfter = testChat
        remoteDataSource.addChatToServer(testChatAfter)

        // Then - Verify new operations have timestamps newer than the previous sync point
        val deltaResult = remoteDataSource.chatsDeltaUpdates(null).first()
        assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(deltaResult)

        val delta = deltaResult.data
        assertTrue(delta.changes.isNotEmpty())

        val chatDelta = delta.changes.first()
        assertIs<ChatCreatedDelta>(chatDelta)

        // The timestamp should advance forward from the previous timestamp (deterministic but forward-progressing)
        assertTrue(
            chatDelta.timestamp > timestampBeforeClear,
            "New chat timestamp ${chatDelta.timestamp} should be newer than timestamp " +
                "before clear $timestampBeforeClear",
        )
    }

    @Test
    fun `chatsDeltaUpdates handles concurrent modifications safely`() = runTest {
        // Given - Start collecting delta updates (simulates sync loop)
        val deltaFlow = remoteDataSource.chatsDeltaUpdates(null)

        // When - Concurrently add chats while delta flow is active
        deltaFlow.test {
            // First emission should be empty
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(initialResult)
            assertEquals(0, initialResult.data.changes.size)

            // Add multiple chats rapidly (simulates debug data generation)
            // This used to cause ConcurrentModificationException
            val numberOfChats = 100
            for (i in 0..numberOfChats) {
                remoteDataSource.addChatToServer(
                    testChat.copy(id = testChatIdWithSuffix(i)),
                )
            }

            // Should receive delta updates without errors
            for (i in 0..numberOfChats) {
                val deltaResult = awaitItem()
                assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(deltaResult)
                val changes = deltaResult.data.changes
                assertEquals(i + 1, changes.size)
                assertIs<ChatCreatedDelta>(changes[i])
                assertEquals(testChatIdWithSuffix(i), changes[i].chatId)
            }
        }
    }

    @Test
    fun `chatsDeltaUpdates handles sync without new changes`() = runTest {
        remoteDataSource.addChatToServer(testChat)

        remoteDataSource.chatsDeltaUpdates(Instant.fromEpochSeconds(1)).test {
            val deltaResult = awaitItem()
            assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(deltaResult)

            assertEquals(0, deltaResult.data.changes.size)
        }
    }

    @Test
    fun `chatsDeltaUpdates handles sync with new changes`() = runTest {
        remoteDataSource.addChatToServer(testChat)

        remoteDataSource.chatsDeltaUpdates(Instant.fromEpochSeconds(0)).test {
            val deltaResult = awaitItem()
            assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(deltaResult)

            assertEquals(1, deltaResult.data.changes.size)
            assertIs<ChatCreatedDelta>(deltaResult.data.changes[0])
        }
    }
}
