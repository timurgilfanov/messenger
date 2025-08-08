package timur.gilfanov.messenger.data.source.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.data.source.remote.ChatCreatedDelta
import timur.gilfanov.messenger.data.source.remote.ChatDeletedDelta
import timur.gilfanov.messenger.data.source.remote.ChatListDelta
import timur.gilfanov.messenger.data.source.remote.ChatMetadata
import timur.gilfanov.messenger.data.source.remote.ChatUpdatedDelta
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@Category(Component::class)
class LocalSyncDataSourceImplTest {

    private lateinit var database: MessengerDatabase
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var localSyncDataSource: LocalSyncDataSource
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        val context: Context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(
            context,
            MessengerDatabase::class.java,
        ).allowMainThreadQueries().build()

        // Create test DataStore
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { context.preferencesDataStoreFile("test_sync_preferences") },
        )

        localSyncDataSource = LocalSyncDataSourceImpl(
            dataStore = dataStore,
            chatDao = database.chatDao(),
            messageDao = database.messageDao(),
            participantDao = database.participantDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `getLastSyncTimestamp returns null when no sync has occurred`() = runTest {
        // When
        val result = localSyncDataSource.getLastSyncTimestamp()

        // Then
        assertIs<ResultWithError.Success<Instant?, LocalDataSourceError>>(result)
        assertNull(result.data)
    }

    @Test
    fun `updateLastSyncTimestamp successfully updates timestamp`() = runTest {
        // Given
        val timestamp = Instant.fromEpochMilliseconds(2000000)

        // When
        val updateResult = localSyncDataSource.updateLastSyncTimestamp(timestamp)
        val getResult = localSyncDataSource.getLastSyncTimestamp()

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(updateResult)
        assertIs<ResultWithError.Success<Instant?, LocalDataSourceError>>(getResult)
        assertEquals(timestamp, getResult.data)
    }

    @Test
    fun `applyChatDelta with ChatCreatedDelta creates new chat`() = runTest {
        // Given
        val chatId = ChatId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
        val participants = createTestParticipants()
        val messages = createTestMessages(chatId)

        val delta = ChatCreatedDelta(
            chatId = chatId,
            chatMetadata = ChatMetadata(
                name = "New Chat",
                participants = participants,
                pictureUrl = null,
                rules = persistentSetOf(),
                unreadMessagesCount = 0,
                lastReadMessageId = null,
                lastActivityAt = null,
            ),
            initialMessages = messages,
            timestamp = Instant.fromEpochMilliseconds(2000000),
        )

        // When
        val result = localSyncDataSource.applyChatDelta(delta)

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify chat was created
        val storedChat = database.chatDao().getChatById(chatId.id.toString())
        assertNotNull(storedChat)
        assertEquals("New Chat", storedChat.name)

        // Verify messages were created
        val storedMessages = database.messageDao().getMessagesByChatId(chatId.id.toString())
        assertEquals(messages.size, storedMessages.size)
    }

    @Test
    fun `applyChatDelta with ChatUpdatedDelta updates existing chat`() = runTest {
        // Given - Create initial chat
        val chatId = ChatId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"))
        val initialDelta = ChatCreatedDelta(
            chatId = chatId,
            chatMetadata = ChatMetadata(
                name = "Initial Chat",
                participants = createTestParticipants(),
                pictureUrl = null,
                rules = persistentSetOf(),
                unreadMessagesCount = 0,
                lastReadMessageId = null,
                lastActivityAt = null,
            ),
            initialMessages = persistentListOf(),
            timestamp = Instant.fromEpochMilliseconds(1000000),
        )
        localSyncDataSource.applyChatDelta(initialDelta)

        // When - Update the chat
        val newMessage = createTestMessages(chatId).first()
        val updateDelta = ChatUpdatedDelta(
            chatId = chatId,
            chatMetadata = ChatMetadata(
                name = "Updated Chat",
                participants = createTestParticipants(),
                pictureUrl = null,
                rules = persistentSetOf(),
                unreadMessagesCount = 1,
                lastReadMessageId = null,
                lastActivityAt = null,
            ),
            messagesToAdd = persistentListOf(newMessage),
            messagesToDelete = persistentListOf(),
            timestamp = Instant.fromEpochMilliseconds(2000000),
        )
        val result = localSyncDataSource.applyChatDelta(updateDelta)

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify chat was updated
        val storedChat = database.chatDao().getChatById(chatId.id.toString())
        assertNotNull(storedChat)
        assertEquals("Updated Chat", storedChat.name)
        assertEquals(1, storedChat.unreadMessagesCount)

        // Verify message was added
        val storedMessages = database.messageDao().getMessagesByChatId(chatId.id.toString())
        assertEquals(1, storedMessages.size)
    }

    @Test
    fun `applyChatDelta with ChatDeletedDelta removes chat`() = runTest {
        // Given - Create initial chat
        val chatId = ChatId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"))
        val initialDelta = ChatCreatedDelta(
            chatId = chatId,
            chatMetadata = ChatMetadata(
                name = "Chat to Delete",
                participants = createTestParticipants(),
                pictureUrl = null,
                rules = persistentSetOf(),
                unreadMessagesCount = 0,
                lastReadMessageId = null,
                lastActivityAt = null,
            ),
            initialMessages = persistentListOf(),
            timestamp = Instant.fromEpochMilliseconds(1000000),
        )
        localSyncDataSource.applyChatDelta(initialDelta)

        // When - Delete the chat
        val deleteDelta = ChatDeletedDelta(
            chatId = chatId,
            timestamp = Instant.fromEpochMilliseconds(2000000),
        )
        val result = localSyncDataSource.applyChatDelta(deleteDelta)

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify chat was deleted
        val storedChat = database.chatDao().getChatById(chatId.id.toString())
        assertNull(storedChat)
    }

    @Test
    fun `applyChatListDelta applies multiple deltas in order`() = runTest {
        // Given
        val chatId1 = ChatId(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"))
        val chatId2 = ChatId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"))

        val delta1 = ChatCreatedDelta(
            chatId = chatId1,
            chatMetadata = ChatMetadata(
                name = "Chat 1",
                participants = createTestParticipants(),
                pictureUrl = null,
                rules = persistentSetOf(),
                unreadMessagesCount = 0,
                lastReadMessageId = null,
                lastActivityAt = null,
            ),
            initialMessages = persistentListOf(),
            timestamp = Instant.fromEpochMilliseconds(1000000),
        )

        val delta2 = ChatCreatedDelta(
            chatId = chatId2,
            chatMetadata = ChatMetadata(
                name = "Chat 2",
                participants = createTestParticipants(),
                pictureUrl = null,
                rules = persistentSetOf(),
                unreadMessagesCount = 0,
                lastReadMessageId = null,
                lastActivityAt = null,
            ),
            initialMessages = persistentListOf(),
            timestamp = Instant.fromEpochMilliseconds(2000000),
        )

        val chatListDelta = ChatListDelta(
            changes = persistentListOf(delta1, delta2),
            fromTimestamp = null,
            toTimestamp = Instant.fromEpochMilliseconds(2000000),
            hasMoreChanges = false,
        )

        // When
        val result = localSyncDataSource.applyChatListDelta(chatListDelta)

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(result)

        // Verify both chats were created
        val storedChat1 = database.chatDao().getChatById(chatId1.id.toString())
        assertNotNull(storedChat1)
        assertEquals("Chat 1", storedChat1.name)

        val storedChat2 = database.chatDao().getChatById(chatId2.id.toString())
        assertNotNull(storedChat2)
        assertEquals("Chat 2", storedChat2.name)

        // Verify sync timestamp was updated in DataStore
        val timestampResult = localSyncDataSource.getLastSyncTimestamp()
        assertIs<ResultWithError.Success<Instant?, LocalDataSourceError>>(timestampResult)
        assertEquals(
            chatListDelta.toTimestamp.toEpochMilliseconds(),
            timestampResult.data?.toEpochMilliseconds(),
        )
    }

    private fun createTestParticipants() = persistentSetOf(
        Participant(
            id = ParticipantId(UUID.fromString("11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
            name = "User 1",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(1000000),
            onlineAt = null,
            isAdmin = false,
            isModerator = false,
        ),
        Participant(
            id = ParticipantId(UUID.fromString("22222222-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
            name = "User 2",
            pictureUrl = null,
            joinedAt = Instant.fromEpochMilliseconds(1100000),
            onlineAt = null,
            isAdmin = true,
            isModerator = false,
        ),
    )

    private fun createTestMessages(chatId: ChatId) = persistentListOf(
        TextMessage(
            id = MessageId(UUID.fromString("33333333-cccc-cccc-cccc-cccccccccccc")),
            recipient = chatId,
            sender = Participant(
                id = ParticipantId(UUID.fromString("11111111-aaaa-aaaa-aaaa-aaaaaaaaaaaa")),
                name = "User 1",
                pictureUrl = null,
                joinedAt = Instant.fromEpochMilliseconds(1000000),
                onlineAt = null,
                isAdmin = false,
                isModerator = false,
            ),
            parentId = null,
            text = "Test message",
            deliveryStatus = DeliveryStatus.Sent,
            createdAt = Instant.fromEpochMilliseconds(1500000),
            sentAt = Instant.fromEpochMilliseconds(1500000),
            deliveredAt = null,
            editedAt = null,
        ),
    )
}
