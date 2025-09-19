package timur.gilfanov.messenger.debug

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import timur.gilfanov.messenger.TestLogger
import timur.gilfanov.messenger.data.source.local.database.MessengerDatabase
import timur.gilfanov.messenger.debug.datastore.DebugPreferences
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage

/**
 * Test utilities for debug code testing.
 * Provides common test data, mocks, and setup utilities.
 */
object DebugTestData {

    /**
     * Creates an in-memory Room database for testing
     */
    fun createTestDatabase(): MessengerDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        MessengerDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()

    /**
     * Creates a mock DataStore for testing debug preferences
     */
    fun createMockDataStore(): DataStore<Preferences> = createTestDataStore()

    /**
     * Creates a test DataStore with predefined preferences
     */
    fun createTestDataStore(scenario: DataScenario = DataScenario.STANDARD): FakeDataStore {
        val preferences = preferencesOf(
            DebugPreferences.DATA_SCENARIO to scenario.name,
            DebugPreferences.LAST_USED_SCENARIO to scenario.name,
            DebugPreferences.AUTO_ACTIVITY_ENABLED to false,
        )
        return FakeDataStore(preferences)
    }

    /**
     * Creates a test logger for debug operations
     */
    fun createTestLogger(): TestLogger = TestLogger()

    /**
     * Creates a test participant
     */
    fun createTestParticipant(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test User",
    ): Participant = Participant(
        id = ParticipantId(
            if (id.startsWith("participant-")) {
                UUID.fromString(id.substring(12)) // Remove "participant-" prefix
            } else {
                UUID.fromString(id)
            },
        ),
        name = name,
        pictureUrl = null,
        joinedAt = Instant.fromEpochMilliseconds(100),
        onlineAt = Instant.fromEpochMilliseconds(200),
    )

    /**
     * Creates a test message
     */
    fun createTestMessage(
        chatId: ChatId,
        sender: Participant = createTestParticipant(),
        text: String = "Test message",
    ): TextMessage = TextMessage(
        id = MessageId(UUID.randomUUID()),
        text = text,
        parentId = null,
        sender = sender,
        recipient = chatId,
        createdAt = Instant.fromEpochMilliseconds(300),
        deliveryStatus = DeliveryStatus.Read,
    )

    /**
     * Creates a test chat
     */
    fun createTestChat(
        id: String = UUID.randomUUID().toString(),
        name: String = "Test Chat",
        participantCount: Int = 2,
        messageCount: Int = 1,
    ): Chat {
        val chatId = ChatId(
            if (id.startsWith("chat-")) {
                UUID.fromString(id.substring(5)) // Remove "chat-" prefix
            } else {
                UUID.fromString(id)
            },
        )

        val participants = (1..participantCount).map { index ->
            createTestParticipant(name = "User $index")
        }.toPersistentSet()

        val messages = (1..messageCount).map { index ->
            createTestMessage(
                chatId = chatId,
                sender = participants.first(),
                text = "Message $index",
            )
        }.toPersistentList()

        return Chat(
            id = chatId,
            participants = participants,
            name = name,
            pictureUrl = null,
            rules = persistentSetOf(),
            unreadMessagesCount = 0,
            lastReadMessageId = null,
            messages = messages,
        )
    }

    /**
     * Creates multiple test chats for different scenarios
     */
    fun createTestChatsForScenario(scenario: DataScenario): List<Chat> {
        val config = scenario.toConfig()
        return (1..config.chatCount).map { index ->
            createTestChat(
                name = "Chat $index",
                participantCount = 2,
                messageCount = config.messageCountRange.first,
            )
        }
    }

    /**
     * Fake DataStore implementation for testing
     */
    class FakeDataStore(private val initialPreferences: Preferences = preferencesOf()) :
        DataStore<Preferences> {

        private val _data = MutableStateFlow(initialPreferences)
        override val data: Flow<Preferences> = _data

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val newPrefs = transform(_data.value)
            _data.value = newPrefs
            return newPrefs
        }
    }

    /**
     * Constants for testing
     */
    object Constants {
        const val TEST_CHAT_COUNT = 3
        const val TEST_PARTICIPANT_COUNT = 2
        const val TEST_MESSAGE_COUNT = 5
        const val TEST_TIMESTAMP = 1000L

        val STANDARD_CONFIG = DataGenerationConfig(
            scenario = DataScenario.STANDARD,
            chatCount = 5,
            messageCountRange = 1..10,
        )
    }
}
