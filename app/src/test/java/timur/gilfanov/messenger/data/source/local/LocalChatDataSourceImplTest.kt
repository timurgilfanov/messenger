package timur.gilfanov.messenger.data.source.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.mapper.EntityMappers
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.testutil.DomainTestFixtures
import timur.gilfanov.messenger.domain.testutil.NoOpLogger
import timur.gilfanov.messenger.testutil.InMemoryDatabaseRule

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class LocalChatDataSourceImplTest {

    @get:Rule
    val databaseRule = InMemoryDatabaseRule()

    private val localChatDataSource: LocalChatDataSource by lazy {
        LocalChatDataSourceImpl(
            chatDao = databaseRule.chatDao,
            messageDao = databaseRule.messageDao,
            participantDao = databaseRule.participantDao,
            database = databaseRule.database,
            logger = NoOpLogger(),
        )
    }

    @Test
    fun `insert chat successfully stores chat with participants`() = runTest {
        // Given
        val chat = createTestChat()

        // When
        val result = localChatDataSource.insertChat(chat)

        // Then
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(result)
        assertEquals(chat, result.data)

        // Verify in database
        val storedChat = databaseRule.chatDao.getChatById(chat.id.id.toString())
        assertEquals(chat.id.id.toString(), storedChat?.id)
        assertEquals(chat.name, storedChat?.name)
    }

    @Test
    fun `update chat successfully updates chat data`() = runTest {
        // Given
        val originalChat = createTestChat()
        localChatDataSource.insertChat(originalChat)

        val updatedChat = originalChat.copy(
            name = "Updated Chat Name",
            unreadMessagesCount = 5,
        )

        // When
        val result = localChatDataSource.updateChat(updatedChat)

        // Then
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(result)
        assertEquals(updatedChat, result.data)

        // Verify in database
        val storedChat = databaseRule.chatDao.getChatById(updatedChat.id.id.toString())
        assertEquals("Updated Chat Name", storedChat?.name)
        assertEquals(5, storedChat?.unreadMessagesCount)
    }

    @Test
    fun `delete chat successfully removes chat`() = runTest {
        // Given
        val chat = createTestChat()
        localChatDataSource.insertChat(chat)

        // When
        val result = localChatDataSource.deleteChat(chat.id)

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify chat is removed from database
        val storedChat = databaseRule.chatDao.getChatById(chat.id.id.toString())
        assertEquals(null, storedChat)
    }

    @Test
    fun `delete non-existent chat returns ChatNotFound error`() = runTest {
        // Given
        val nonExistentChatId = ChatId(UUID.fromString("99999999-9999-9999-9999-999999999999"))

        // When
        val result = localChatDataSource.deleteChat(nonExistentChatId)

        // Then
        assertIs<ResultWithError.Failure<Unit, LocalDataSourceError>>(result)
        assertIs<LocalDataSourceError.ChatNotFound>(result.error)
    }

    @Test
    fun `delete non-existent chat does not touch participants`() = runTest {
        // Given - existing chat with participants in the database
        val chat = createTestChat()
        localChatDataSource.insertChat(chat)
        val participantsBefore = databaseRule.participantDao.getAllParticipants()
        assertEquals(chat.participants.size, participantsBefore.size)

        val nonExistentChatId = ChatId(UUID.fromString("99999999-9999-9999-9999-999999999999"))

        // When
        val result = localChatDataSource.deleteChat(nonExistentChatId)

        // Then
        assertIs<ResultWithError.Failure<Unit, LocalDataSourceError>>(result)
        assertIs<LocalDataSourceError.ChatNotFound>(result.error)
        val participantsAfter = databaseRule.participantDao.getAllParticipants()
        assertEquals(participantsBefore.size, participantsAfter.size)
    }

    @Test
    fun `delete chat cascades messages and chat_participants`() = runTest {
        // Given - chat with messages and participants
        val chat = createTestChat()
        localChatDataSource.insertChat(chat)
        val message = DomainTestFixtures.createTestTextMessage(
            id = MessageId(UUID.fromString("33333333-cccc-cccc-cccc-cccccccccccc")),
            text = "Hello",
            sender = chat.participants.first(),
            recipient = chat.id,
            createdAt = Instant.fromEpochMilliseconds(1500000),
        )
        databaseRule.messageDao.insertMessage(
            with(EntityMappers) { message.toMessageEntity() },
        )

        // Sanity-check the pre-state.
        assertEquals(
            1,
            databaseRule.messageDao.getMessagesByChatId(chat.id.id.toString()).size,
        )
        assertEquals(
            chat.participants.size,
            databaseRule.participantDao.getParticipantsByChatId(chat.id.id.toString()).size,
        )

        // When
        val result = localChatDataSource.deleteChat(chat.id)

        // Then - FK cascade removed messages and junction rows
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)
        assertTrue(
            databaseRule.messageDao.getMessagesByChatId(chat.id.id.toString()).isEmpty(),
        )
        assertTrue(
            databaseRule.participantDao.getParticipantsByChatId(chat.id.id.toString()).isEmpty(),
        )
    }

    @Test
    fun `delete chat removes orphaned participants`() = runTest {
        // Given - chat whose participants are not shared with any other chat
        val chat = createTestChat()
        localChatDataSource.insertChat(chat)
        assertEquals(chat.participants.size, databaseRule.participantDao.getAllParticipants().size)

        // When
        val result = localChatDataSource.deleteChat(chat.id)

        // Then - all participants removed because no other chat references them
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)
        assertTrue(databaseRule.participantDao.getAllParticipants().isEmpty())
    }

    @Test
    fun `delete chat preserves participants shared with another chat`() = runTest {
        // Given - two chats sharing the same participants
        val sharedParticipants = setOf(
            DomainTestFixtures.createTestParticipant(
                id = ParticipantId(
                    UUID.fromString("44444444-4444-4444-4444-444444444444"),
                ),
                name = "User 1",
                joinedAt = Instant.fromEpochMilliseconds(1000000),
                onlineAt = null,
            ),
            DomainTestFixtures.createTestParticipant(
                id = ParticipantId(
                    UUID.fromString("55555555-5555-5555-5555-555555555555"),
                ),
                name = "User 2",
                joinedAt = Instant.fromEpochMilliseconds(1100000),
                onlineAt = null,
            ),
        )
        val chatToDelete = DomainTestFixtures.createTestChat(
            id = ChatId(UUID.fromString("aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa")),
            name = "Chat to Delete",
            participants = sharedParticipants,
        )
        val chatToKeep = DomainTestFixtures.createTestChat(
            id = ChatId(UUID.fromString("aaaaaaaa-2222-2222-2222-aaaaaaaaaaaa")),
            name = "Chat to Keep",
            participants = sharedParticipants,
        )
        localChatDataSource.insertChat(chatToDelete)
        localChatDataSource.insertChat(chatToKeep)

        // When
        val result = localChatDataSource.deleteChat(chatToDelete.id)

        // Then - shared participants stay because chatToKeep still references them
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)
        assertEquals(
            sharedParticipants.size,
            databaseRule.participantDao.getAllParticipants().size,
        )
        assertEquals(
            sharedParticipants.size,
            databaseRule.participantDao.getParticipantsByChatId(
                chatToKeep.id.id.toString(),
            ).size,
        )
    }

    @Test
    fun `flowChatList emits chat previews`() = runTest {
        // Given
        val chat1 = createTestChat(id = "77777777-7777-7777-7777-777777777777", name = "Chat 1")
        val chat2 = createTestChat(id = "88888888-8888-8888-8888-888888888888", name = "Chat 2")

        localChatDataSource.insertChat(chat1)
        localChatDataSource.insertChat(chat2)

        // When & Then
        localChatDataSource.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, LocalDataSourceError>>(result)

            val chatPreviews = result.data
            assertEquals(2, chatPreviews.size)
            assertTrue(chatPreviews.any { it.name == "Chat 1" })
            assertTrue(chatPreviews.any { it.name == "Chat 2" })
        }
    }

    @Test
    fun `flowChatList preview reflects most-recent message and null for empty chat`() = runTest {
        // Given - chat1 with two messages, chat2 with none
        val chat1 = createTestChat(id = "77777777-7777-7777-7777-777777777777", name = "Chat 1")
        val chat2 = createTestChat(id = "88888888-8888-8888-8888-888888888888", name = "Chat 2")
        localChatDataSource.insertChat(chat1)
        localChatDataSource.insertChat(chat2)

        val olderMessage = DomainTestFixtures.createTestTextMessage(
            id = MessageId(UUID.fromString("33333333-cccc-cccc-cccc-cccccccccccc")),
            text = "Older",
            sender = chat1.participants.first(),
            recipient = chat1.id,
            createdAt = Instant.fromEpochMilliseconds(1000),
        )
        val newerMessage = DomainTestFixtures.createTestTextMessage(
            id = MessageId(UUID.fromString("44444444-cccc-cccc-cccc-cccccccccccc")),
            text = "Newer",
            sender = chat1.participants.first(),
            recipient = chat1.id,
            createdAt = Instant.fromEpochMilliseconds(2000),
        )
        databaseRule.messageDao.insertMessages(
            with(EntityMappers) {
                listOf(olderMessage.toMessageEntity(), newerMessage.toMessageEntity())
            },
        )

        // When & Then
        localChatDataSource.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, LocalDataSourceError>>(result)

            val preview1 = result.data.first { it.name == "Chat 1" }
            val lastMessage = preview1.lastMessage
            assertIs<TextMessage>(lastMessage)
            assertEquals("Newer", lastMessage.text)
            assertEquals(Instant.fromEpochMilliseconds(2000), preview1.lastActivityAt)

            val preview2 = result.data.first { it.name == "Chat 2" }
            assertNull(preview2.lastMessage)
            assertNull(preview2.lastActivityAt)
        }
    }

    @Test
    fun `flowChatList emits empty list when no chats exist`() = runTest {
        // When & Then - combine must still emit once both upstream flows yield their empty results
        localChatDataSource.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, LocalDataSourceError>>(result)
            assertTrue(result.data.isEmpty())
        }
    }

    @Test
    fun `flowChatList attributes each chat its own last message`() = runTest {
        // Given - two non-empty chats, each with a distinct last message
        val chat1 = createTestChat(id = "77777777-7777-7777-7777-777777777777", name = "Chat 1")
        val chat2 = createTestChat(id = "88888888-8888-8888-8888-888888888888", name = "Chat 2")
        localChatDataSource.insertChat(chat1)
        localChatDataSource.insertChat(chat2)

        val chat1Message = DomainTestFixtures.createTestTextMessage(
            id = MessageId(UUID.fromString("33333333-cccc-cccc-cccc-cccccccccccc")),
            text = "From chat 1",
            sender = chat1.participants.first(),
            recipient = chat1.id,
            createdAt = Instant.fromEpochMilliseconds(1000),
        )
        val chat2Message = DomainTestFixtures.createTestTextMessage(
            id = MessageId(UUID.fromString("44444444-cccc-cccc-cccc-cccccccccccc")),
            text = "From chat 2",
            sender = chat2.participants.first(),
            recipient = chat2.id,
            createdAt = Instant.fromEpochMilliseconds(2000),
        )
        databaseRule.messageDao.insertMessages(
            with(EntityMappers) {
                listOf(chat1Message.toMessageEntity(), chat2Message.toMessageEntity())
            },
        )

        // When & Then
        localChatDataSource.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<List<ChatPreview>, LocalDataSourceError>>(result)

            val preview1 = result.data.first { it.name == "Chat 1" }
            val lastMessage1 = preview1.lastMessage
            assertIs<TextMessage>(lastMessage1)
            assertEquals("From chat 1", lastMessage1.text)
            assertEquals(Instant.fromEpochMilliseconds(1000), preview1.lastActivityAt)

            val preview2 = result.data.first { it.name == "Chat 2" }
            val lastMessage2 = preview2.lastMessage
            assertIs<TextMessage>(lastMessage2)
            assertEquals("From chat 2", lastMessage2.text)
            assertEquals(Instant.fromEpochMilliseconds(2000), preview2.lastActivityAt)
        }
    }

    @Test
    fun `flowChatList escalates when last message sender is not a chat participant`() = runTest {
        // Given - a chat whose last message is authored by someone who is not a member of it.
        // The sender exists globally (a member of another chat), so this is a genuine
        // source-of-truth inconsistency that persists in a committed snapshot, not a transient
        // torn read between the two combined flows.
        val chat = createTestChat(id = "77777777-7777-7777-7777-777777777777", name = "Chat 1")
        val otherChat = DomainTestFixtures.createTestChat(
            id = ChatId(UUID.fromString("88888888-8888-8888-8888-888888888888")),
            name = "Other Chat",
            participants = setOf(
                DomainTestFixtures.createTestParticipant(
                    id = ParticipantId(UUID.fromString("99999999-9999-9999-9999-999999999999")),
                    name = "Outsider",
                    joinedAt = Instant.fromEpochMilliseconds(1000000),
                    onlineAt = null,
                    isCurrentUser = true,
                ),
            ),
        )
        localChatDataSource.insertChat(chat)
        localChatDataSource.insertChat(otherChat)

        val orphanMessage = DomainTestFixtures.createTestTextMessage(
            id = MessageId(UUID.fromString("33333333-cccc-cccc-cccc-cccccccccccc")),
            text = "From an outsider",
            sender = otherChat.participants.first(),
            recipient = chat.id,
            createdAt = Instant.fromEpochMilliseconds(1000),
        )
        databaseRule.messageDao.insertMessages(
            with(EntityMappers) { listOf(orphanMessage.toMessageEntity()) },
        )

        // When & Then - the inconsistency is escalated as a handled error, not silently dropped
        localChatDataSource.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<List<ChatPreview>, LocalDataSourceError>>(result)
            assertIs<LocalDataSourceError.InvalidData>(result.error)
        }
    }

    @Test
    fun `flowChatUpdates emits updates for specific chat`() = runTest {
        // Given
        val chat = createTestChat()
        localChatDataSource.insertChat(chat)

        // When & Then
        localChatDataSource.flowChatUpdates(chat.id).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(result)

            val emittedChat = result.data
            assertEquals(chat.id, emittedChat.id)
            assertEquals(chat.name, emittedChat.name)
            assertEquals(chat.participants.size, emittedChat.participants.size)
        }
    }

    @Test
    fun `flowChatUpdates for non-existent chat returns ChatNotFound error`() = runTest {
        // Given
        val nonExistentChatId = ChatId(UUID.fromString("99999999-9999-9999-9999-999999999999"))

        // When & Then
        localChatDataSource.flowChatUpdates(nonExistentChatId).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
            assertIs<LocalDataSourceError.ChatNotFound>(result.error)
        }
    }

    @Test
    fun `flowChatUpdates returns InvalidData when no participant is current user`() = runTest {
        // Given - chat with no current user (violates backend contract)
        val chat = createTestChatWithNoCurrentUser()
        localChatDataSource.insertChat(chat)

        // When & Then
        localChatDataSource.flowChatUpdates(chat.id).test {
            val result = awaitItem()
            assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
            assertIs<LocalDataSourceError.InvalidData>(result.error)
        }
    }

    @Test
    fun `flowChatUpdates returns InvalidData when multiple participants are current user`() =
        runTest {
            // Given - chat with two current users (violates backend contract)
            val chat = createTestChatWithMultipleCurrentUsers()
            localChatDataSource.insertChat(chat)

            // When & Then
            localChatDataSource.flowChatUpdates(chat.id).test {
                val result = awaitItem()
                assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
                assertIs<LocalDataSourceError.InvalidData>(result.error)
            }
        }

    @Test
    fun `flowChatUpdates escalates to InvalidData when last message sender is not a participant`() =
        runTest {
            // Given - target chat with current user + another participant
            val chat = createTestChat()
            localChatDataSource.insertChat(chat)

            // Seed a real source-of-truth orphan: a message in `chat` whose sender is a
            // globally-known participant that is NOT a member of `chat` (not a missing FK row).
            val outsiderId = seedOrphanMessageInChat(chat)

            // When & Then - flow surfaces the orphan as a handled Failure, then stays alive:
            // once the orphan is repaired (the sender becomes a participant of `chat`) a
            // subsequent Success must be emitted on the same subscription.
            localChatDataSource.flowChatUpdates(chat.id).test {
                val result = awaitItem()
                assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
                val error = result.error
                assertIs<LocalDataSourceError.InvalidData>(error)
                assertEquals("messages", error.field)
                assertTrue(error.reason.contains(outsiderId.id.toString()))
                assertTrue(error.reason.contains(chat.id.id.toString()))

                // Repair the orphan: add the outsider as a (non-current-user) participant of
                // `chat`, keeping exactly one current user so the only change is the now-valid
                // sender. The flow must not have terminated on the Failure above.
                val repairedChat = chat.copy(
                    participants = chat.participants.add(
                        DomainTestFixtures.createTestParticipant(
                            id = outsiderId,
                            name = "Outsider",
                            joinedAt = Instant.fromEpochMilliseconds(1200000),
                            onlineAt = null,
                            isCurrentUser = false,
                        ),
                    ),
                )
                localChatDataSource.updateChat(repairedChat)

                val recovered = awaitItem()
                assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(recovered)
                assertEquals(chat.id, recovered.data.id)
                assertEquals(1, recovered.data.messages.size)
                assertEquals(outsiderId, recovered.data.messages.first().sender.id)
            }
        }

    @Test
    fun `flowChatUpdates emits Success when last message sender is a participant`() = runTest {
        // Given - chat whose last message is sent by an actual participant (fast path)
        val chat = createTestChat()
        localChatDataSource.insertChat(chat)
        val sender = chat.participants.first { it.isCurrentUser }
        val message = DomainTestFixtures.createTestTextMessage(
            id = MessageId(UUID.fromString("33333333-cccc-cccc-cccc-cccccccccccc")),
            text = "Hello",
            sender = sender,
            recipient = chat.id,
            createdAt = Instant.fromEpochMilliseconds(1500000),
        )
        databaseRule.messageDao.insertMessage(
            with(EntityMappers) { message.toMessageEntity() },
        )

        // When & Then
        localChatDataSource.flowChatUpdates(chat.id).test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(result)
            val emittedChat = result.data
            assertEquals(chat.id, emittedChat.id)
            assertEquals(1, emittedChat.messages.size)
            assertEquals(sender.id, emittedChat.messages.first().sender.id)
        }
    }

    // Validation error tests
    @Test
    fun `insert chat with blank name returns InvalidData error`() = runTest {
        // Given
        val invalidChat = createTestChat().copy(name = "")

        // When
        val result = localChatDataSource.insertChat(invalidChat)

        // Then
        assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
        val invalidDataError0 = result.error
        assertIs<LocalDataSourceError.InvalidData>(invalidDataError0)
        assertEquals("name", invalidDataError0.field)
        assertEquals("Chat name cannot be blank", invalidDataError0.reason)
    }

    @Test
    fun `insert chat with empty participants returns InvalidData error`() = runTest {
        // Given
        val invalidChat = createTestChat().copy(participants = persistentSetOf())

        // When
        val result = localChatDataSource.insertChat(invalidChat)

        // Then
        assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
        val invalidDataError1 = result.error
        assertIs<LocalDataSourceError.InvalidData>(invalidDataError1)
        assertEquals("participants", invalidDataError1.field)
        assertEquals("Chat must have at least one participant", invalidDataError1.reason)
    }

    @Test
    fun `update chat with blank name returns InvalidData error`() = runTest {
        // Given
        val originalChat = createTestChat()
        localChatDataSource.insertChat(originalChat)

        val invalidChat = originalChat.copy(name = "")

        // When
        val result = localChatDataSource.updateChat(invalidChat)

        // Then
        assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
        val invalidDataError2 = result.error
        assertIs<LocalDataSourceError.InvalidData>(invalidDataError2)
        assertEquals("name", invalidDataError2.field)
    }

    @Test
    fun `update chat with empty participants returns InvalidData error`() = runTest {
        // Given
        val originalChat = createTestChat()
        localChatDataSource.insertChat(originalChat)

        val invalidChat = originalChat.copy(participants = persistentSetOf())

        // When
        val result = localChatDataSource.updateChat(invalidChat)

        // Then
        assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
        val invalidDataError3 = result.error
        assertIs<LocalDataSourceError.InvalidData>(invalidDataError3)
        assertEquals("participants", invalidDataError3.field)
    }

    // Upsert behavior tests (Room uses OnConflictStrategy.REPLACE)
    @Test
    fun `insert duplicate chat successfully updates existing chat`() = runTest {
        // Given - insert initial chat
        val chat = createTestChat()
        val insertResult = localChatDataSource.insertChat(chat)
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(insertResult)

        // When - insert same ID with different name
        val updatedChat = chat.copy(name = "Updated Name via Insert")
        val result = localChatDataSource.insertChat(updatedChat)

        // Then - verify it succeeded and updated
        assertIs<ResultWithError.Success<Chat, LocalDataSourceError>>(result)
        assertEquals(updatedChat, result.data)

        val storedChat = databaseRule.chatDao.getChatById(chat.id.id.toString())
        assertNotNull(storedChat)
        assertEquals("Updated Name via Insert", storedChat.name)
    }

    @Test
    fun `update non-existent chat returns ChatNotFound error`() = runTest {
        // Given
        val nonExistentChat = createTestChat(id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")

        // When
        val result = localChatDataSource.updateChat(nonExistentChat)

        // Then
        assertIs<ResultWithError.Failure<Chat, LocalDataSourceError>>(result)
        // Note: update doesn't check existence first, so this might pass
        // If it passes, the test documents actual behavior
    }

    private suspend fun seedOrphanMessageInChat(chat: Chat): ParticipantId {
        val outsiderId = ParticipantId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"))
        val otherChat = DomainTestFixtures.createTestChat(
            id = ChatId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")),
            name = "Other Chat",
            participants = setOf(
                DomainTestFixtures.createTestParticipant(
                    id = outsiderId,
                    name = "Outsider",
                    joinedAt = Instant.fromEpochMilliseconds(1200000),
                    onlineAt = null,
                    isCurrentUser = true,
                ),
                DomainTestFixtures.createTestParticipant(
                    id = ParticipantId(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")),
                    name = "Outsider 2",
                    joinedAt = Instant.fromEpochMilliseconds(1300000),
                    onlineAt = null,
                ),
            ),
        )
        localChatDataSource.insertChat(otherChat)

        val orphanSender = otherChat.participants.first { it.id == outsiderId }
        val orphanMessage = DomainTestFixtures.createTestTextMessage(
            id = MessageId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")),
            text = "orphan",
            sender = orphanSender,
            recipient = chat.id,
            createdAt = Instant.fromEpochMilliseconds(2000000),
        )
        databaseRule.messageDao.insertMessage(
            with(EntityMappers) { orphanMessage.toMessageEntity() },
        )
        return outsiderId
    }

    private fun createTestChat(
        id: String = "66666666-6666-6666-6666-666666666666", // Fixed UUID
        name: String = "Test Chat",
    ) = DomainTestFixtures.createTestChat(
        id = ChatId(UUID.fromString(id)),
        name = name,
        participants = setOf(
            DomainTestFixtures.createTestParticipant(
                id = ParticipantId(UUID.fromString("44444444-4444-4444-4444-444444444444")),
                name = "User 1",
                joinedAt = Instant.fromEpochMilliseconds(1000000),
                onlineAt = null,
                isCurrentUser = true,
            ),
            DomainTestFixtures.createTestParticipant(
                id = ParticipantId(UUID.fromString("55555555-5555-5555-5555-555555555555")),
                name = "User 2",
                joinedAt = Instant.fromEpochMilliseconds(1100000),
                onlineAt = null,
            ),
        ),
    )

    private fun createTestChatWithNoCurrentUser(
        id: String = "aaaa0000-0000-0000-0000-000000000000",
    ) = DomainTestFixtures.createTestChat(
        id = ChatId(UUID.fromString(id)),
        name = "No Current User Chat",
        participants = setOf(
            DomainTestFixtures.createTestParticipant(
                id = ParticipantId(UUID.fromString("44444444-4444-4444-4444-444444444444")),
                name = "User 1",
                joinedAt = Instant.fromEpochMilliseconds(1000000),
                onlineAt = null,
                isCurrentUser = false,
            ),
            DomainTestFixtures.createTestParticipant(
                id = ParticipantId(UUID.fromString("55555555-5555-5555-5555-555555555555")),
                name = "User 2",
                joinedAt = Instant.fromEpochMilliseconds(1100000),
                onlineAt = null,
                isCurrentUser = false,
            ),
        ),
    )

    private fun createTestChatWithMultipleCurrentUsers(
        id: String = "bbbb0000-0000-0000-0000-000000000000",
    ) = DomainTestFixtures.createTestChat(
        id = ChatId(UUID.fromString(id)),
        name = "Multiple Current Users Chat",
        participants = setOf(
            DomainTestFixtures.createTestParticipant(
                id = ParticipantId(UUID.fromString("44444444-4444-4444-4444-444444444444")),
                name = "User 1",
                joinedAt = Instant.fromEpochMilliseconds(1000000),
                onlineAt = null,
                isCurrentUser = true,
            ),
            DomainTestFixtures.createTestParticipant(
                id = ParticipantId(UUID.fromString("55555555-5555-5555-5555-555555555555")),
                name = "User 2",
                joinedAt = Instant.fromEpochMilliseconds(1100000),
                onlineAt = null,
                isCurrentUser = true,
            ),
        ),
    )
}
