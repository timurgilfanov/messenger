package timur.gilfanov.messenger.data.repository

import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.TestLogger
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDataSourceFake
import timur.gilfanov.messenger.data.source.local.LocalDataSources
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteDataSources
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.chat.FlowChatListError
import timur.gilfanov.messenger.domain.usecase.chat.MarkMessagesAsReadError
import timur.gilfanov.messenger.domain.usecase.chat.ReceiveChatUpdatesError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryCreateChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryDeleteChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryJoinChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryLeaveChatError
import timur.gilfanov.messenger.domain.usecase.chat.RepositoryMarkMessagesAsReadError
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode.FOR_SENDER_ONLY
import timur.gilfanov.messenger.domain.usecase.message.RepositoryDeleteMessageError
import timur.gilfanov.messenger.domain.usecase.message.RepositoryEditMessageError
import timur.gilfanov.messenger.domain.usecase.message.RepositorySendMessageError
import timur.gilfanov.messenger.util.NoOpLogger

@OptIn(ExperimentalCoroutinesApi::class)
@Category(Unit::class)
@Suppress("LargeClass") // Comprehensive repository test suite
class MessengerRepositoryImplTest {

    private val logger = TestLogger()
    private lateinit var localDataSource: LocalDataSourceFake
    private lateinit var remoteDataSource: RemoteDataSourceFake
    private lateinit var repository: MessengerRepositoryImpl

    private lateinit var testChat: Chat
    private lateinit var testChatPreview: ChatPreview
    private lateinit var testMessage: TextMessage
    private lateinit var testParticipant: Participant

    @Before
    fun setup() {
        localDataSource = LocalDataSourceFake(logger)
        remoteDataSource = RemoteDataSourceFake()

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
            rules = persistentSetOf<timur.gilfanov.messenger.domain.entity.chat.Rule>(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            messages = persistentListOf(testMessage),
        )

        testChatPreview = ChatPreview.fromChat(testChat)
    }

    // MessageRepository tests
    @Test
    fun `sendMessage should store message locally and emit remote updates`() = runTest {
        // Given: Set up fake data sources with initial chat
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(1_000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)

        // Create repository after data source setup
        repository = repositoryImpl(backgroundScope)

        val messageToSend = testMessage.copy(
            id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440004")),
            text = "New message to send",
            deliveryStatus = null,
        )

        // When: Send message
        repository.sendMessage(messageToSend).test {
            // Then: Should receive sending progress updates
            val result0 = awaitItem()
            assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(result0)
            assertEquals(messageToSend.text, (result0.data as TextMessage).text)
            assertIs<DeliveryStatus.Sending>(result0.data.deliveryStatus)
            assertEquals(0, result0.data.deliveryStatus.progress)

            val result50 = awaitItem()
            assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(result50)
            assertEquals(messageToSend.text, (result50.data as TextMessage).text)
            assertIs<DeliveryStatus.Sending>(result50.data.deliveryStatus)
            assertEquals(50, result50.data.deliveryStatus.progress)

            val result100 = awaitItem()
            assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(result100)
            assertEquals(messageToSend.text, (result100.data as TextMessage).text)
            assertIs<DeliveryStatus.Sending>(result100.data.deliveryStatus)
            assertEquals(100, result100.data.deliveryStatus.progress)

            val resultDelivered = awaitItem()
            assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(resultDelivered)
            assertEquals(messageToSend.text, (resultDelivered.data as TextMessage).text)
            assertEquals(DeliveryStatus.Delivered, resultDelivered.data.deliveryStatus)

            val resultRead = awaitItem()
            assertIs<ResultWithError.Success<Message, RepositorySendMessageError>>(resultRead)
            assertEquals(messageToSend.text, (resultRead.data as TextMessage).text)
            assertEquals(DeliveryStatus.Read, resultRead.data.deliveryStatus)

            localDataSource.getMessage(messageToSend.id).let {
                assertIs<ResultWithError.Success<TextMessage, LocalDataSourceError>>(it)
                assertEquals(messageToSend.id, it.data.id)
                assertIs<DeliveryStatus.Read>(it.data.deliveryStatus)
            }

            awaitComplete()
        }
    }

    // ChatRepository tests
    @Test
    fun `createChat should store chat locally and remotely`() = runTest {
        // Given: Set up repository
        repository = repositoryImpl(backgroundScope)

        // When: Create chat
        val result = repository.createChat(testChat)

        // Then: Should succeed
        assertIs<ResultWithError.Success<Chat, RepositoryCreateChatError>>(result)
        assertEquals(testChat, result.data)

        // And: Chat should be stored locally
        val storedChatResult = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(storedChatResult)
        assertEquals(testChat, storedChatResult.data)
    }

    @Test
    fun `flowChatList returns local chat list`() = runTest {
        // Given: Set up repository with chat in local storage
        localDataSource.insertChat(testChat)
        repository = repositoryImpl(backgroundScope)

        // When: Get chat list
        repository.flowChatList().test {
            // Then: Should return chat list
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(result)
            assertEquals(1, result.data.size)
            assertEquals(testChatPreview.id, result.data[0].id)
            assertEquals(testChatPreview.name, result.data[0].name)
        }
    }

    @Test
    fun `isChatListUpdating initially returns false`() = runTest {
        repository = repositoryImpl(backgroundScope)
        repository.flowChatList().test {
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(initialResult)
            assertEquals(0, initialResult.data.size)

            repository.isChatListUpdating().test {
                assertEquals(false, awaitItem())
            }
        }
    }

    @Test
    fun `deleteChat should remove from local and remote`() = runTest {
        // Given: Set up repository with chat
        localDataSource.insertChat(testChat)
        remoteDataSource.addChatToServer(testChat)
        repository = repositoryImpl(backgroundScope)

        localDataSource.flowChatList().test {
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(initialResult)
            assertEquals(1, initialResult.data.size)
            assertEquals(testChat.id, initialResult.data[0].id)

            val result = repository.deleteChat(testChat.id)
            assertIs<ResultWithError.Success<Unit, RepositoryDeleteChatError>>(result)

            val finalResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(finalResult)
            assertEquals(0, finalResult.data.size)
        }
    }

    @Test
    fun `joinChat should return chat and store locally`() = runTest {
        remoteDataSource.addChatToServer(testChat)
        repository = repositoryImpl(backgroundScope)

        val result = repository.joinChat(testChat.id, null)

        assertIs<ResultWithError.Success<Chat, RepositoryJoinChatError>>(result)
        assertEquals(testChat, result.data)

        val storedChatResult = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(storedChatResult)
        assertEquals(testChat, storedChatResult.data)
    }

    @Test
    fun `leaveChat should remove from local storage`() = runTest {
        localDataSource.insertChat(testChat)
        remoteDataSource.addChatToServer(testChat)
        repository = repositoryImpl(backgroundScope)

        repository.flowChatList().test {
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(initialResult)
            assertEquals(1, initialResult.data.size)
            assertEquals(testChat.id, initialResult.data[0].id)

            val result = repository.leaveChat(testChat.id)
            assertIs<ResultWithError.Success<Unit, RepositoryLeaveChatError>>(result)

            val finalResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(finalResult)
            assertEquals(0, finalResult.data.size)

            val deletedChatResult = localDataSource.getChat(testChat.id)
            assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(deletedChatResult)
            assertEquals(LocalDataSourceError.ChatNotFound, deletedChatResult.error)
        }
    }

    @Test
    fun `receiveChatUpdates should return chat updates`() = runTest {
        // Given: Set up repository with chat in local storage
        localDataSource.insertChat(testChat)
        repository = repositoryImpl(backgroundScope)

        // When: Receive chat updates
        repository.receiveChatUpdates(testChat.id).test {
            // Then: Should return chat
            val result = awaitItem()
            assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(result)
            assertEquals(testChat, result.data)
        }
    }

    @Test
    fun `editMessage should update message`() = runTest {
        // Given: Set up repository with chat and message
        localDataSource.insertChat(testChat)
        remoteDataSource.addChatToServer(testChat)
        repository = repositoryImpl(backgroundScope)

        val editedMessage = testMessage.copy(
            text = "Edited message text",
            deliveryStatus = null,
        )

        // When: Edit message
        repository.editMessage(editedMessage).test {
            // Then: Should return edited message
            val result = awaitItem()
            assertIs<ResultWithError.Success<Message, RepositoryEditMessageError>>(result)
            assertEquals(editedMessage.text, (result.data as TextMessage).text)
            // Note: RemoteDataSourceFake returns the message as-is without changing delivery status
            assertNull(result.data.deliveryStatus)
            awaitComplete()
        }
    }

    @Test
    fun `deleteMessage should remove message`() = runTest {
        // Given: Set up repository with chat and message
        localDataSource.insertChat(testChat)
        remoteDataSource.addChatToServer(testChat)
        repository = repositoryImpl(backgroundScope)

        // When: Delete message
        val result = repository.deleteMessage(testMessage.id, FOR_SENDER_ONLY)

        // Then: Should succeed
        assertIs<ResultWithError.Success<Unit, RepositoryDeleteMessageError>>(result)
    }

    @Test
    fun `flowChatList should start with local data and update with remote`() = runTest {
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(1000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)

        repository = repositoryImpl(backgroundScope)

        repository.flowChatList().test {
            val firstResult = awaitItem()
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

            val secondResult = awaitItem()
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
        repository = repositoryImpl(backgroundScope)

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
    fun `flowChatList should return empty local data when both sources fail`() = runTest {
        // Given: Simulate network disconnection and no local data
        remoteDataSource.setConnectionState(false)

        // Create repository after data source setup
        repository = repositoryImpl(backgroundScope)

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
        repository = repositoryImpl(backgroundScope)

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
        repository = repositoryImpl(backgroundScope)

        // When & Then: Should return success with empty local data (local-first always works)
        repository.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(result)
            assertEquals(1, result.data.size) // Empty because no local data and remote failed
            assertEquals(testChat.id, result.data.first().id)
        }
    }

    @Test
    fun `isChatListUpdating should change to true when delta sync occurs`() = runTest {
        // Given: Repository is created with empty state
        repository = repositoryImpl(backgroundScope)

        repository.flowChatList().test {
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(initialResult)
            assertEquals(0, initialResult.data.size)

            val chatListTestScope = this

            // When & Then: Test updating state changes during sync
            repository.isChatListUpdating().test {
                assertEquals(false, awaitItem())

                // Add chat to remote to trigger delta sync
                remoteDataSource.addChatToServer(testChat)

                // Should emit true when delta sync starts processing
                assertEquals(true, awaitItem())

                val finalResult = chatListTestScope.awaitItem()
                assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(finalResult)
                assertEquals(1, finalResult.data.size)
                assertEquals(testChat.id, finalResult.data[0].id)

                // Should emit false when delta sync completes
                assertEquals(false, awaitItem())
            }
        }
    }

    @Test
    fun `deleteMessage should return error when message not found`() = runTest {
        // Given: Message doesn't exist
        val nonExistentMessageId = MessageId(
            UUID.fromString(
                "550e8400-e29b-41d4-a716-446655440999",
            ),
        )

        // Create repository after data source setup
        repository = repositoryImpl(backgroundScope)

        // When: Try to delete non-existent message
        val result = repository.deleteMessage(
            nonExistentMessageId,
            FOR_SENDER_ONLY,
        )

        // Then: Should return error
        assertIs<ResultWithError.Failure<Unit, RepositoryDeleteMessageError>>(result)
    }

    @Test
    fun `deleteMessage should sync remote deletion to local storage via delta updates`() = runTest {
        // Given: Message exists in both local and remote sources
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(1000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)

        // Create repository after data source setup
        repository = repositoryImpl(backgroundScope)

        // Verify message exists in local storage initially
        localDataSource.getMessage(testMessage.id).let {
            assertIs<ResultWithError.Success<TextMessage, LocalDataSourceError>>(it)
            assertEquals(testMessage.id, it.data.id)
        }

        // Then: Verify local storage is updated through delta sync via chat list
        repository.flowChatList().test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(firstResult)
            assertEquals(1, firstResult.data.size)
            val firstChatPreview = firstResult.data.first()
            assertEquals(testChat.id, firstChatPreview.id)
            assertEquals(testMessage.id, firstChatPreview.lastMessage?.id)

            // When: Delete message from remote source only (simulating external deletion)
            remoteDataSource.deleteMessageFromServerChat(testMessage.id)

            val secondResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(secondResult)
            assertEquals(1, secondResult.data.size)
            val secondChatPreview = secondResult.data.first()
            assertEquals(testChat.id, secondChatPreview.id)
            assertEquals(null, secondChatPreview.lastMessage) // No messages left

            // Verify message is deleted from local storage
            localDataSource.getMessage(testMessage.id).let {
                assertIs<ResultWithError.Failure<TextMessage, LocalDataSourceError>>(it)
                assertEquals(LocalDataSourceError.MessageNotFound, it.error)
            }
        }
    }

    @Test
    fun `deleteChat should sync remote deletion to local storage via delta updates`() = runTest {
        // Given: Chat exists in both local and remote sources
        localDataSource.insertChat(testChat)
        val syncTimestamp = Instant.fromEpochMilliseconds(1000)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(testChat)

        // Create repository after data source setup
        repository = repositoryImpl(backgroundScope)

        // Verify chat exists in local storage initially
        localDataSource.getChat(testChat.id).let {
            assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(it)
            assertEquals(testChat.id, it.data.id)
        }

        // Then: Verify local storage is updated through delta sync via chat list
        repository.flowChatList().test {
            val firstResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(firstResult)
            assertEquals(1, firstResult.data.size)
            val firstChatPreview = firstResult.data.first()
            assertEquals(testChat.id, firstChatPreview.id)

            // When: Delete chat from remote source only (simulating external deletion)
            remoteDataSource.deleteChatFromServer(testChat.id)

            val secondResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(secondResult)
            assertEquals(0, secondResult.data.size)

            // Verify chat is deleted from local storage
            localDataSource.getChat(testChat.id).let {
                assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(it)
                assertEquals(LocalDataSourceError.ChatNotFound, it.error)
            }
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

            // Create repository after data source setup
            repository = repositoryImpl(backgroundScope)

            // When & Then: Should receive initial chat data and updates
            repository.receiveChatUpdates(testChat.id).test {
                val firstResult = awaitItem()
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

                val secondResult = awaitItem()
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
    fun `joinChat should return error when chat not found`() = runTest {
        // Given: Chat doesn't exist
        val nonExistentChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440888"))

        // Create repository after data source setup
        repository = repositoryImpl(backgroundScope)

        // When: Try to join non-existent chat
        val result = repository.joinChat(nonExistentChatId, null)

        // Then: Should return error
        assertIs<ResultWithError.Failure<Chat, RepositoryJoinChatError>>(result)
        assertEquals(RepositoryJoinChatError.ChatNotFound, result.error)
    }

    @Test
    fun `leaveChat should return error when chat not found`() = runTest {
        // Given: Chat doesn't exist
        val nonExistentChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440777"))

        // Create repository after data source setup
        repository = repositoryImpl(backgroundScope)

        // When: Try to leave non-existent chat
        val result = repository.leaveChat(nonExistentChatId)

        // Then: Should return error
        assertIs<ResultWithError.Failure<Unit, RepositoryLeaveChatError>>(result)
        assertEquals(RepositoryLeaveChatError.ChatNotFound, result.error)
    }

    @Test
    fun `repository should handle getLastSyncTimestamp failure during initialization`() = runTest {
        // Given: Local data source fails to get sync timestamp
        localDataSource.simulateGetLastSyncTimestampFailure(true)

        // When: Repository is created (this triggers performDeltaSyncLoop)
        repository = repositoryImpl(backgroundScope)

        // Then: Repository should handle the failure gracefully and continue working
        // The delta sync should start from null timestamp (from scratch)
        repository.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(result)
            assertEquals(0, result.data.size) // Empty initially
        }

        // Cleanup
        localDataSource.simulateGetLastSyncTimestampFailure(false)
    }

    @Test
    fun `flowChatList should handle local data source failures`() = runTest {
        // Given: Local data source fails to provide chat list
        localDataSource.simulateFlowChatListFailure(true)

        // Create repository after setting up failure
        repository = repositoryImpl(backgroundScope)

        // When & Then: Should return failure with LocalError
        repository.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<List<ChatPreview>, FlowChatListError>>(result)
            assertEquals(FlowChatListError.LocalError, result.error)
        }

        // Cleanup
        localDataSource.simulateFlowChatListFailure(false)
    }

    @Test
    fun `sendMessage should handle network failures`() = runTest {
        localDataSource.insertChat(testChat)
        remoteDataSource.setConnectionState(false)

        repository = repositoryImpl(backgroundScope)

        val message = TextMessage(
            id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440004")),
            text = "Test message",
            parentId = null,
            sender = testParticipant,
            recipient = testChat.id,
            createdAt = Instant.fromEpochMilliseconds(101_000),
            deliveryStatus = DeliveryStatus.Sending(0),
        )

        repository.sendMessage(message).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, RepositorySendMessageError>>(result)
            assertEquals(RepositorySendMessageError.NetworkNotAvailable, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `deleteMessage should handle message not found error`() = runTest {
        val nonExistentMessageId = MessageId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440666"),
        )

        repository = repositoryImpl(backgroundScope)

        val result = repository.deleteMessage(nonExistentMessageId, FOR_SENDER_ONLY)

        assertIs<ResultWithError.Failure<Unit, RepositoryDeleteMessageError>>(result)
        assertEquals(RepositoryDeleteMessageError.MessageNotFound, result.error)
    }

    @Test
    fun `receiveChatUpdates should handle non-existent chat`() = runTest {
        repository = repositoryImpl(backgroundScope)
        val nonExistentChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440555"))

        repository.receiveChatUpdates(nonExistentChatId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, ReceiveChatUpdatesError>>(result)
            assertEquals(ReceiveChatUpdatesError.ChatNotFound, result.error)
        }
    }

    @Test
    fun `joinChat should handle various remote failures`() = runTest {
        repository = repositoryImpl(backgroundScope)
        val nonExistentChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440444"))

        val result = repository.joinChat(nonExistentChatId, null)

        assertIs<ResultWithError.Failure<Chat, RepositoryJoinChatError>>(result)
        assertEquals(RepositoryJoinChatError.ChatNotFound, result.error)
    }

    @Test
    fun `joinChat should handle network failures`() = runTest {
        remoteDataSource.addChatToServer(testChat)
        remoteDataSource.setConnectionState(false)

        repository = repositoryImpl(backgroundScope)

        val result = repository.joinChat(testChat.id, null)

        assertIs<ResultWithError.Failure<Chat, RepositoryJoinChatError>>(result)
        assertEquals(RepositoryJoinChatError.NetworkNotAvailable, result.error)
    }

    @Test
    fun `leaveChat should handle network failures`() = runTest {
        localDataSource.insertChat(testChat)
        remoteDataSource.addChatToServer(testChat)
        remoteDataSource.setConnectionState(false)

        repository = repositoryImpl(backgroundScope)

        val result = repository.leaveChat(testChat.id)

        assertIs<ResultWithError.Failure<Unit, RepositoryLeaveChatError>>(result)
        assertEquals(RepositoryLeaveChatError.NetworkNotAvailable, result.error)
    }

    @Test
    fun `editMessage should handle network failures`() = runTest {
        localDataSource.insertChat(testChat)
        remoteDataSource.setConnectionState(false)

        repository = repositoryImpl(backgroundScope)

        val updatedMessage = testMessage.copy(text = "Updated text")

        repository.editMessage(updatedMessage).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Message, RepositoryEditMessageError>>(result)
            assertEquals(RepositoryEditMessageError.NetworkNotAvailable, result.error)
            awaitComplete()
        }
    }

    @Test
    fun `delta sync should handle network failures gracefully`() = runTest {
        repository = repositoryImpl(backgroundScope)

        repository.flowChatList().test {
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, FlowChatListError>>(initialResult)
            assertEquals(0, initialResult.data.size)

            remoteDataSource.setConnectionState(false)
            remoteDataSource.addChatToServer(testChat)

            val timestampResult = localDataSource.getLastSyncTimestamp()
            assertIs<ResultWithError.Success<Instant?, LocalDataSourceError>>(
                timestampResult,
            )
            assertNull(timestampResult.data)
            localDataSource.getChat(testChat.id).let {
                assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(it)
                assertEquals(LocalDataSourceError.ChatNotFound, it.error)
            }
        }
    }

    @Test
    fun `createChat should return error when network unavailable`() = runTest {
        // Given: Network is disconnected
        remoteDataSource.setConnectionState(false)
        repository = repositoryImpl(backgroundScope)

        // When: Try to create chat
        val result = repository.createChat(testChat)

        // Then: Should return network error
        assertIs<ResultWithError.Failure<Chat, RepositoryCreateChatError>>(result)
        assertEquals(RepositoryCreateChatError.NetworkNotAvailable, result.error)

        // Verify chat was not stored locally
        val localChat = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Failure<Chat, *>>(localChat)
    }

    @Test
    fun `deleteChat should return error when chat not found`() = runTest {
        // Given: Chat doesn't exist
        val nonExistentChatId = ChatId(
            UUID.fromString("550e8400-e29b-41d4-a716-446655440333"),
        )
        repository = repositoryImpl(backgroundScope)

        // When: Try to delete non-existent chat
        val result = repository.deleteChat(nonExistentChatId)

        // Then: Should return error
        assertIs<ResultWithError.Failure<Unit, RepositoryDeleteChatError>>(result)
        assertEquals(RepositoryDeleteChatError.ChatNotFound(nonExistentChatId), result.error)
    }

    @Test
    fun `deleteChat should return error when network unavailable`() = runTest {
        // Given: Chat exists locally but network is disconnected
        localDataSource.insertChat(testChat)
        remoteDataSource.setConnectionState(false)
        repository = repositoryImpl(backgroundScope)

        // When: Try to delete chat
        val result = repository.deleteChat(testChat.id)

        // Then: Should return network error
        assertIs<ResultWithError.Failure<Unit, RepositoryDeleteChatError>>(result)
        assertEquals(RepositoryDeleteChatError.NetworkNotAvailable, result.error)

        // Verify chat still exists locally (remote-first approach failed)
        val localChat = localDataSource.getChat(testChat.id)
        assertIs<ResultWithError.Success<Chat, *>>(localChat)
    }

    @Test
    fun `markMessagesAsRead should succeed when remote call succeeds`() = runTest {
        // Given: Chat with unread messages
        val chatWithUnreadMessages = testChat.copy(
            messages = persistentListOf(testMessage),
            unreadMessagesCount = 1,
            lastReadMessageId = null,
        )
        localDataSource.insertChat(chatWithUnreadMessages)
        remoteDataSource.addChatToServer(chatWithUnreadMessages)
        repository = repositoryImpl(backgroundScope)

        // When: Mark messages as read
        val result = repository.markMessagesAsRead(testChat.id, testMessage.id)

        // Then: Should succeed
        assertIs<ResultWithError.Success<Unit, RepositoryMarkMessagesAsReadError>>(result)
    }

    @Test
    fun `markMessagesAsRead should return NetworkNotAvailable error when network unavailable`() =
        runTest {
            // Given: Chat exists but network is disconnected
            localDataSource.insertChat(testChat)
            remoteDataSource.addChatToServer(testChat)
            remoteDataSource.setConnectionState(false)
            repository = repositoryImpl(backgroundScope)

            // When: Try to mark messages as read
            val result = repository.markMessagesAsRead(testChat.id, testMessage.id)

            // Then: Should return network error
            assertIs<ResultWithError.Failure<Unit, RepositoryMarkMessagesAsReadError>>(result)
            assertEquals(MarkMessagesAsReadError.NetworkNotAvailable, result.error)
        }

    @Test
    fun `markMessagesAsRead should return ChatNotFound error when chat doesn't exist`() = runTest {
        // Given: Chat doesn't exist
        val nonExistentChatId = ChatId(UUID.fromString("550e8400-e29b-41d4-a716-446655440222"))
        repository = repositoryImpl(backgroundScope)

        // When: Try to mark messages as read for non-existent chat
        val result = repository.markMessagesAsRead(nonExistentChatId, testMessage.id)

        // Then: Should return error
        assertIs<ResultWithError.Failure<Unit, RepositoryMarkMessagesAsReadError>>(result)
        assertEquals(MarkMessagesAsReadError.ChatNotFound, result.error)
    }

    @Test
    fun `markMessagesAsRead should propagate changes via delta sync loop`() = runTest {
        // Given: Chat with multiple unread messages
        val message1 = testMessage.copy(
            id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440010")),
            createdAt = Instant.fromEpochMilliseconds(1000),
        )
        val message2 = testMessage.copy(
            id = MessageId(UUID.fromString("550e8400-e29b-41d4-a716-446655440011")),
            createdAt = Instant.fromEpochMilliseconds(2000),
        )
        val chatWithUnreadMessages = testChat.copy(
            messages = persistentListOf(message1, message2),
            unreadMessagesCount = 2,
            lastReadMessageId = null,
        )

        localDataSource.insertChat(chatWithUnreadMessages)
        val syncTimestamp = Instant.fromEpochMilliseconds(500)
        localDataSource.updateLastSyncTimestamp(syncTimestamp)
        remoteDataSource.addChatToServer(chatWithUnreadMessages)

        repository = repositoryImpl(backgroundScope)

        // When & Then: Verify changes propagate through delta sync
        repository.receiveChatUpdates(testChat.id).test {
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(initialResult)
            assertEquals(2, initialResult.data.unreadMessagesCount)
            assertEquals(null, initialResult.data.lastReadMessageId)

            // Mark first message as read
            val markAsReadResult = repository.markMessagesAsRead(testChat.id, message1.id)
            assertIs<ResultWithError.Success<Unit, RepositoryMarkMessagesAsReadError>>(
                markAsReadResult,
            )

            // Should receive updated chat with reduced unread count
            val updatedResult = awaitItem()
            assertIs<ResultWithError.Success<Chat, ReceiveChatUpdatesError>>(updatedResult)
            assertEquals(1, updatedResult.data.unreadMessagesCount) // Only message2 left unread
            assertEquals(message1.id, updatedResult.data.lastReadMessageId)
        }
    }

    private fun repositoryImpl(scope: CoroutineScope): MessengerRepositoryImpl =
        MessengerRepositoryImpl(
            localDataSources = LocalDataSources(
                chat = localDataSource,
                message = localDataSource,
                sync = localDataSource,
            ),
            remoteDataSources = RemoteDataSources(
                chat = remoteDataSource,
                message = remoteDataSource,
                sync = remoteDataSource,
            ),
            logger = NoOpLogger(),
            repositoryScope = scope,
        )
}
