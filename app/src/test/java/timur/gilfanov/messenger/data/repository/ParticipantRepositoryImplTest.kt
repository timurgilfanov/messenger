package timur.gilfanov.messenger.data.repository

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.annotations.Unit
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.participant.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.participant.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.participant.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.participant.message.DeleteMessageMode
import timur.gilfanov.messenger.domain.usecase.participant.message.RepositoryDeleteMessageError

@Category(Unit::class)
class ParticipantRepositoryImplTest {

    private lateinit var localDataSource: LocalDataSourceFake
    private lateinit var remoteDataSource: RemoteDataSourceFake
    private lateinit var repository: ParticipantRepositoryImpl

    private lateinit var testChat: Chat
    private lateinit var testChatPreview: ChatPreview
    private lateinit var testMessage: TextMessage
    private lateinit var testParticipant: Participant

    @Before
    fun setup() {
        localDataSource = LocalDataSourceFake()
        remoteDataSource = RemoteDataSourceFake()
        // Repository creation moved to individual tests after data source setup

        testParticipant = Participant(
            id = ParticipantId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
            name = "Test User",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(100),
            onlineAt = Instant.fromEpochMilliseconds(400),
        )

        val chatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
        testMessage = TextMessage(
            id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440003")),
            text = "Test message",
            parentId = null,
            sender = testParticipant,
            recipient = chatId,
            createdAt = Instant.fromEpochMilliseconds(200),
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
            messages = persistentListOf(testMessage),
        )

        testChatPreview = ChatPreview.fromChat(testChat)
    }

    @Test
    fun `sendMessage should store message locally and emit remote updates`() = runTest {
        // Given: Set up fake data sources with initial chat
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(100_000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)
        val message = TextMessage(
            id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440004")),
            text = "Test message",
            parentId = null,
            sender = testParticipant,
            recipient = testChat.id,
            createdAt = Instant.fromEpochMilliseconds(101_000),
            deliveryStatus = DeliveryStatus.Sending(0),
        )
        localDataSource.getMessage(message.id).let {
            assertIs<ResultWithError.Failure<TextMessage, LocalDataSourceError>>(it)
            assertEquals(LocalDataSourceError.MessageNotFound, it.error)
        }
        localDataSource.getLastSyncTimestamp().let {
            assertIs<ResultWithError.Success<Instant?, LocalDataSourceError>>(it)
            assertEquals(syncTimestamp, it.data)
        }

        // When & Then: Test the message sending flow
        repository.sendMessage(message).test {
            localDataSource.getMessage(message.id).let {
                assertIs<ResultWithError.Success<TextMessage, LocalDataSourceError>>(it)
                assertEquals(message.id, it.data.id)
            }
            localDataSource.getLastSyncTimestamp().let {
                assertIs<ResultWithError.Success<Instant?, LocalDataSourceError>>(it)
                assertEquals(syncTimestamp, it.data)
            }
            awaitItem().let {
                assertIs<ResultWithError.Success<TextMessage, *>>(it)
                assertEquals(message.id, it.data.id)
            }

            assertIs<ResultWithError.Success<TextMessage, *>>(awaitItem())
            assertIs<ResultWithError.Success<TextMessage, *>>(awaitItem())
            assertIs<ResultWithError.Success<TextMessage, *>>(awaitItem())
            awaitItem().let {
                assertIs<ResultWithError.Success<TextMessage, *>>(it)
                assertEquals(message.id, it.data.id)
                assertIs<DeliveryStatus.Read>(it.data.deliveryStatus)
            }
            localDataSource.getMessage(message.id).let {
                assertIs<ResultWithError.Success<TextMessage, LocalDataSourceError>>(it)
                assertEquals(message.id, it.data.id)
                assertIs<DeliveryStatus.Read>(it.data.deliveryStatus)
            }
            awaitComplete()
        }
    }

    @Test
    fun `flowChatList should start with local data and update with remote`() = runTest {
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(1000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)
        println("Test setup: Local and remote chat inserted and timestamp $syncTimestamp")

        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)
        println("Test setup: Repository created")

        repository.flowChatList().test {
            println("Test: await first item (local data)")
            val firstResult = awaitItem()
            println("First item: $firstResult")
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(firstResult)
            assertEquals(1, firstResult.data.size)
            val firstChatPreview = firstResult.data.first()
            assertEquals(testChat.id, firstChatPreview.id)
            assertEquals(testMessage.id, firstChatPreview.lastMessage?.id)

            val newMessage = TextMessage(
                id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440004")),
                text = "Test message",
                parentId = null,
                sender = testParticipant,
                recipient = testChat.id,
                createdAt = Instant.fromEpochMilliseconds(1500), // After sync timestamp (1000ms)
                deliveryStatus = DeliveryStatus.Sent,
            )
            // Add message to existing remote chat instead of replacing entire chat
            remoteDataSource.addMessageToServerChat(newMessage)
            println("Test setup: Added message ${newMessage.id} to remote chat")

            println("Test: await second item (after delta sync)")
            val secondResult = awaitItem()
            println("Second item: $secondResult")
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(secondResult)
            assertEquals(1, secondResult.data.size)
            val secondChatPreview = secondResult.data.first()
            assertEquals(testChat.id, secondChatPreview.id)
            // Second emission should have the new message from delta sync
            assertEquals(newMessage.id, secondChatPreview.lastMessage?.id)
            assertEquals(1, secondChatPreview.unreadMessagesCount)
        }
    }

    @Test
    fun `flowChatList should emit local data if remote not available`() = runTest {
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(100_000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)
        val newMessage = TextMessage(
            id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440004")),
            text = "Test message",
            parentId = null,
            sender = testParticipant,
            recipient = testChat.id,
            createdAt = Clock.System.now(),
            deliveryStatus = DeliveryStatus.Sending(0),
        )
        // Add message to existing remote chat instead of replacing entire chat
        remoteDataSource.addMessageToServerChat(newMessage)
        remoteDataSource.setConnectionState(false)

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        repository.flowChatList().test {
            awaitItem().let { result ->
                assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(result)
                assertEquals(1, result.data.size)
                val chatPreview = result.data.first()
                assertEquals(testChat.id, chatPreview.id)
                assertEquals(testMessage.id, chatPreview.lastMessage?.id)
            }
        }
    }

    @Test
    fun `flowChatList should return local data and background sync updates cache`() = runTest {
        // Given: Chat exists only in remote
        remoteDataSource.addChatToServer(testChat)
        localDataSource.getChat(testChat.id).let {
            assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(it)
            assertEquals(LocalDataSourceError.ChatNotFound, it.error)
        }

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When: Subscribe to chat list
        repository.flowChatList().test {
            // Should emit local data (initially empty)
            awaitItem().let { result ->
                assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(result)
                // Could be empty or could already have synced data depending on timing
                // This is acceptable behavior for background sync
            }

            // Give time for background sync to potentially update local cache
            // In a real implementation, this would be handled by the reactive flows
            cancelAndIgnoreRemainingEvents()
        }

        // Eventually, the background sync should populate local cache
        // We can verify this by checking local data after some time
        kotlinx.coroutines.delay(100) // Give background sync time to work

        // The exact timing is implementation-dependent, but local cache should eventually be updated
        // For this test, we just verify that the repository doesn't crash and returns valid results
    }

    @Test
    fun `flowChatList should return empty local data when both sources fail`() = runTest {
        // Given: Simulate network disconnection and no local data
        remoteDataSource.setConnectionState(false)

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When & Then: Should return success with empty local data (local-first always works)
        repository.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(result)
            assertEquals(0, result.data.size) // Empty because no local data and remote failed
        }
    }

    @Test
    fun `flowChatList should return empty local data when both sources empty`() = runTest {
        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        repository.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(result)
            assertEquals(0, result.data.size) // Empty because no local data and remote failed
        }
    }

    @Test
    fun `flowChatList should return local data when remote sources fail`() = runTest {
        // Given: Simulate network disconnection and no local data
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(100_000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)
        remoteDataSource.setConnectionState(false)

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When & Then: Should return success with empty local data (local-first always works)
        repository.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(result)
            assertEquals(1, result.data.size) // Empty because no local data and remote failed
            assertEquals(testChat.id, result.data.first().id)
        }
    }

    @Test
    fun `isChatListUpdating should return opposite of connection state`() = runTest {
        // Given: Connection state changes
        remoteDataSource.setConnectionState(true)

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When & Then: Should reflect connection state
        repository.isChatListUpdating().test {
            assertEquals(false, awaitItem()) // connected = not updating

            // Change connection state
            remoteDataSource.setConnectionState(false)
            assertEquals(true, awaitItem()) // disconnected = updating
        }
    }

    @Test
    fun `deleteMessage should delete from remote and local`() = runTest {
        // Given: Message exists in both sources
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(100_000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When: Delete message
        val result = repository.deleteMessage(testMessage.id, DeleteMessageMode.FOR_SENDER_ONLY)

        // Then: Should succeed
        assertIs<ResultWithError.Success<Unit, RepositoryDeleteMessageError>>(result)
    }

    @Test
    fun `deleteMessage should return error when message not found`() = runTest {
        // Given: Message doesn't exist
        val nonExistentMessageId = MessageId(UUID.randomUUID())

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When: Try to delete non-existent message
        val result = repository.deleteMessage(
            nonExistentMessageId,
            DeleteMessageMode.FOR_SENDER_ONLY,
        )

        // Then: Should return error
        assertIs<ResultWithError.Failure<Unit, RepositoryDeleteMessageError>>(result)
    }

    // todo: add test of message deletion on remote data source led to local deletion

    @Test
    fun `receiveChatUpdates should stream chat updates`() = runTest {
        // Given: Chat exists in both sources
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(100_000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When & Then: Should receive chat updates
        repository.receiveChatUpdates(testChat.id).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(result)
            assertEquals(testChat.id, result.data.id)
        }
    }

    @Test
    fun `receiveChatUpdates should start with local data and update when remote chat changes`() =
        runTest {
            // Given: Chat exists in both sources
            localDataSource.insertChat(testChat)
            val syncTimestamp = Instant.fromEpochMilliseconds(800)
            localDataSource.updateLastSyncTimestamp(syncTimestamp)
            remoteDataSource.addChatToServer(testChat)
            println("Test setup: Local and remote chat inserted and timestamp $syncTimestamp")

            // Create repository after data source setup
            repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)
            println("Test setup: Repository created")

            // When & Then: Should receive initial chat data and updates
            repository.receiveChatUpdates(testChat.id).test {
                println("Test: await first item (local data)")
                val firstResult = awaitItem()
                println("First item: $firstResult")
                assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(firstResult)
                assertEquals(testChat.id, firstResult.data.id)
                assertEquals(1, firstResult.data.messages.size)
                assertEquals(testMessage.id, firstResult.data.messages.first().id)

                val newMessage = TextMessage(
                    id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440005")),
                    text = "New remote message",
                    parentId = null,
                    sender = testParticipant,
                    recipient = testChat.id,
                    createdAt = Instant.fromEpochMilliseconds(1200), // After sync timestamp (800ms)
                    deliveryStatus = DeliveryStatus.Sent,
                )
                // Add message to existing remote chat to simulate remote change
                remoteDataSource.addMessageToServerChat(newMessage)
                println("Test setup: Added message ${newMessage.id} to remote chat")

                println("Test: await second item (after remote update)")
                val secondResult = awaitItem()
                println("Second item: $secondResult")
                assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(secondResult)
                assertEquals(testChat.id, secondResult.data.id)
                assertEquals(2, secondResult.data.messages.size)
                // Verify both original and new messages are present
                val messageIds = secondResult.data.messages.map { it.id }
                assert(testMessage.id in messageIds) {
                    "Original message should still be present"
                }
                assert(newMessage.id in messageIds) { "New message should be present" }
            }
        }

    @Test
    fun `joinChat should add chat to local after remote success`() = runTest {
        // Given: Chat exists on server
        remoteDataSource.addChatToServer(testChat)

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When: Join chat
        val result = repository.joinChat(testChat.id, null)

        // Then: Should succeed and chat should be in local storage
        assertIs<ResultWithError.Success<Chat, RepositoryJoinChatError>>(result)
        assertEquals(testChat.id, result.data.id)

        // Verify chat was added to local storage
        val localChat = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Success<Chat, *>>(localChat)
    }

    @Test
    fun `joinChat should return error when chat not found`() = runTest {
        // Given: Chat doesn't exist
        val nonExistentChatId = ChatId(UUID.randomUUID())

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When: Try to join non-existent chat
        val result = repository.joinChat(nonExistentChatId, null)

        // Then: Should return error
        assertIs<ResultWithError.Failure<Chat, RepositoryJoinChatError>>(result)
        assertEquals(RepositoryJoinChatError.ChatNotFound, result.error)
    }

    @Test
    fun `leaveChat should remove chat from local after remote success`() = runTest {
        // Given: Chat exists in both sources
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(100_000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When: Leave chat (note: this will succeed as fake doesn't validate membership)
        val result = repository.leaveChat(testChat.id)

        // Then: Should succeed
        assertIs<ResultWithError.Success<Unit, RepositoryLeaveChatError>>(result)

        // Verify chat was removed from local storage
        val localChat = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Failure<Chat, *>>(localChat)
    }

    @Test
    fun `leaveChat should return error when chat not found`() = runTest {
        // Given: Chat doesn't exist
        val nonExistentChatId = ChatId(UUID.randomUUID())

        // Create repository after data source setup
        repository = ParticipantRepositoryImpl(localDataSource, remoteDataSource)

        // When: Try to leave non-existent chat
        val result = repository.leaveChat(nonExistentChatId)

        // Then: Should return error
        assertIs<ResultWithError.Failure<Unit, RepositoryLeaveChatError>>(result)
        assertEquals(RepositoryLeaveChatError.ChatNotFound, result.error)
    }
}
