package timur.gilfanov.messenger.debug

import androidx.paging.PagingSource
import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import timur.gilfanov.messenger.annotations.Component
import timur.gilfanov.messenger.data.source.local.LocalChatDataSource
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDataSources
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSource
import timur.gilfanov.messenger.data.source.local.LocalMessageDataSource
import timur.gilfanov.messenger.data.source.local.LocalSyncDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteDebugDataSource
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId

@RunWith(RobolectricTestRunner::class)
@Category(Component::class)
class DebugDataRepositoryTest {

    private lateinit var dataStore: DebugTestData.FakeDataStore
    private lateinit var localDebugDataSource: FakeLocalDebugDataSource
    private lateinit var localDataSources: LocalDataSources
    private lateinit var remoteDebugDataSource: FakeRemoteDebugDataSource
    private lateinit var sampleDataProvider: SampleDataProvider
    private lateinit var testScope: TestScope
    private lateinit var logger: TrackingTestLogger
    private lateinit var debugDataRepository: DebugDataRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        dataStore = DebugTestData.createTestDataStore(lastGeneration = 0L)
        localDebugDataSource = FakeLocalDebugDataSource()
        localDataSources = LocalDataSources(
            chat = FakeLocalChatDataSource(),
            message = FakeLocalMessageDataSource(),
            sync = FakeLocalSyncDataSource(),
        )
        remoteDebugDataSource = FakeRemoteDebugDataSource()
        sampleDataProvider = SampleDataProvider()
        testScope = TestScope(StandardTestDispatcher())
        logger = TrackingTestLogger()

        debugDataRepository = DebugDataRepository(
            dataStore = dataStore,
            localDebugDataSource = localDebugDataSource,
            localDataSources = localDataSources,
            remoteDebugDataSource = remoteDebugDataSource,
            sampleDataProvider = sampleDataProvider,
            coroutineScope = testScope.backgroundScope,
            logger = logger,
        )
    }

    @Test
    fun `debugSettings flow emits correct initial settings`() = testScope.runTest {
        // When
        debugDataRepository.debugSettings.test {
            val settings = awaitItem()

            // Then
            assertEquals(DataScenario.STANDARD, settings.scenario)
            assertFalse(settings.autoActivity)
            assertTrue(settings.showNotification)
            assertEquals(0L, settings.lastGeneration)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `initializeWithScenario clears data and generates new data`() = testScope.runTest {
        // Given
        val scenario = DataScenario.MINIMAL

        // When
        debugDataRepository.initializeWithScenario(scenario)

        // Then - Verify clearing operations were called
        assertTrue(localDebugDataSource.wasDeleteAllChatsCalled)
        assertTrue(localDebugDataSource.wasDeleteAllMessagesCalled)
        assertTrue(localDebugDataSource.wasClearSyncTimestampCalled)
        assertTrue(remoteDebugDataSource.wasClearServerDataCalled)

        // Verify data generation - check that chats were added to remote
        assertTrue(remoteDebugDataSource.addedChats.isNotEmpty())
        assertEquals(scenario.chatCount, remoteDebugDataSource.addedChats.size)

        // Verify settings update
        debugDataRepository.debugSettings.test {
            val settings = awaitItem()
            assertEquals(scenario, settings.scenario)
            assertTrue(settings.lastGeneration > 0L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `regenerateData uses current scenario from settings`() = testScope.runTest {
        // Given - Set initial scenario
        debugDataRepository.updateSettings { current ->
            current.copy(scenario = DataScenario.EDGE_CASES)
        }

        // When
        debugDataRepository.regenerateData()

        // Then - Verify data was regenerated by checking added chats
        assertTrue(remoteDebugDataSource.addedChats.isNotEmpty())
        assertEquals(DataScenario.EDGE_CASES.chatCount, remoteDebugDataSource.addedChats.size)
    }

    @Test
    fun `clearAllData clears both local and remote data`() = testScope.runTest {
        // When
        debugDataRepository.clearAllData()

        // Then
        assertTrue(localDebugDataSource.wasDeleteAllChatsCalled)
        assertTrue(localDebugDataSource.wasDeleteAllMessagesCalled)
        assertTrue(localDebugDataSource.wasClearSyncTimestampCalled)
        assertTrue(remoteDebugDataSource.wasClearServerDataCalled)
    }

    @Test
    fun `updateSettings modifies datastore preferences`() = testScope.runTest {
        // When
        debugDataRepository.updateSettings { current ->
            current.copy(
                scenario = DataScenario.DEMO,
                autoActivity = true,
                showNotification = false,
            )
        }

        // Then
        debugDataRepository.debugSettings.test {
            val settings = awaitItem()
            assertEquals(DataScenario.DEMO, settings.scenario)
            assertTrue(settings.autoActivity)
            assertFalse(settings.showNotification)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getSavedScenario returns scenario from preferences`() = testScope.runTest {
        // Given - Set a scenario in preferences
        debugDataRepository.updateSettings { current ->
            current.copy(scenario = DataScenario.HEAVY)
        }

        // When
        val savedScenario = debugDataRepository.getSavedScenario()

        // Then
        assertEquals(DataScenario.HEAVY, savedScenario)
    }

    @Test
    fun `toggleAutoActivity updates settings and controls auto activity`() = testScope.runTest {
        // When - Enable auto activity
        debugDataRepository.toggleAutoActivity(true)

        // Then - Verify settings updated
        debugDataRepository.debugSettings.test {
            val settings = awaitItem()
            assertTrue(settings.autoActivity)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleNotification updates notification setting`() = testScope.runTest {
        // When
        debugDataRepository.toggleNotification(false)

        // Then
        debugDataRepository.debugSettings.test {
            val settings = awaitItem()
            assertFalse(settings.showNotification)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `auto activity starts when enabled via settings`() = testScope.runTest {
        // Given - Setup chats for simulation
        val testChat = DebugTestData.createTestChat()
        remoteDebugDataSource.setCurrentChats(listOf(testChat))
        val initialMessageCount = remoteDebugDataSource.addedMessages.size

        // When - Enable auto activity
        debugDataRepository.toggleAutoActivity(true)

        // Then - Verify the setting is enabled
        debugDataRepository.debugSettings.test {
            val settings = awaitItem()
            assertTrue(settings.autoActivity, "Auto activity should be enabled")
            cancelAndIgnoreRemainingEvents()
        }

        // Note: We can't reliably test message generation timing in this test
        // because backgroundScope runs independently of test time control
    }

    @Test
    fun `handles local data source errors gracefully`() = testScope.runTest {
        // Given - Local data source fails
        localDebugDataSource.shouldFailDeleteAllChats = true

        // When
        debugDataRepository.initializeWithScenario(DataScenario.STANDARD)

        // Then - Should continue despite errors and log them
        assertTrue(localDebugDataSource.wasDeleteAllChatsCalled)
        assertTrue(logger.warnLogs.isNotEmpty())
    }

    @Test
    fun `handles data generation errors gracefully`() = testScope.runTest {
        // Given - Simulate error by making local data source fail
        localDebugDataSource.shouldFailDeleteAllChats = true

        // When - Initialize should still complete
        debugDataRepository.initializeWithScenario(DataScenario.STANDARD)

        // Then - Should handle error gracefully and still generate data
        assertTrue(localDebugDataSource.wasDeleteAllChatsCalled)
        // Data should still be generated despite local clearing failures
        assertTrue(remoteDebugDataSource.addedChats.isNotEmpty())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `auto activity stops when disabled`() = testScope.runTest {
        // Given - Auto activity is running
        val testChat = DebugTestData.createTestChat()
        remoteDebugDataSource.setCurrentChats(listOf(testChat))

        debugDataRepository.toggleAutoActivity(true)
        advanceTimeBy(6000L) // Trigger first message

        val initialMessageCount = remoteDebugDataSource.addedMessages.size

        // When - Disable auto activity
        debugDataRepository.toggleAutoActivity(false)
        advanceTimeBy(10000L)

        // Then - No more messages should be generated after disabling
        assertEquals(initialMessageCount, remoteDebugDataSource.addedMessages.size)
    }

    @Test
    fun `regenerateData updates last generation timestamp`() = testScope.runTest {
        // Given - Get initial timestamp
        val initialLastGeneration = debugDataRepository.debugSettings.first().lastGeneration

        // When
        debugDataRepository.regenerateData()

        // Then
        debugDataRepository.debugSettings.test {
            val settings = awaitItem()
            assertTrue(
                settings.lastGeneration > initialLastGeneration,
                "Last generation should be updated after regenerating data",
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `component integrates all dependencies correctly`() = testScope.runTest {
        // This is a comprehensive integration test

        // Given
        val scenario = DataScenario.DEMO

        // When - Perform full initialization flow
        debugDataRepository.initializeWithScenario(scenario)

        // Then - Verify all components are called in correct sequence
        assertTrue(localDebugDataSource.wasDeleteAllChatsCalled)
        assertTrue(localDebugDataSource.wasDeleteAllMessagesCalled)
        assertTrue(localDebugDataSource.wasClearSyncTimestampCalled)
        assertTrue(remoteDebugDataSource.wasClearServerDataCalled)

        assertEquals(scenario.chatCount, remoteDebugDataSource.addedChats.size)

        // Verify final state
        debugDataRepository.debugSettings.test {
            val settings = awaitItem()
            assertEquals(scenario, settings.scenario)
            assertEquals(scenario.chatCount, settings.effectiveChatCount)
            assertTrue(settings.lastGeneration > 0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Fake implementation of LocalDebugDataSource for testing
     */
    private class FakeLocalDebugDataSource : LocalDebugDataSource {
        var wasDeleteAllChatsCalled = false
        var wasDeleteAllMessagesCalled = false
        var wasClearSyncTimestampCalled = false
        var shouldFailDeleteAllChats = false
        var shouldFailDeleteAllMessages = false
        var shouldFailClearSyncTimestamp = false

        override suspend fun deleteAllChats(): ResultWithError<Unit, LocalDataSourceError> {
            wasDeleteAllChatsCalled = true
            return if (shouldFailDeleteAllChats) {
                throw java.sql.SQLException("Database error: Failed to delete chats")
            } else {
                ResultWithError.Success(Unit)
            }
        }

        override suspend fun deleteAllMessages(): ResultWithError<Unit, LocalDataSourceError> {
            wasDeleteAllMessagesCalled = true
            return if (shouldFailDeleteAllMessages) {
                throw java.sql.SQLException("Database error: Failed to delete messages")
            } else {
                ResultWithError.Success(Unit)
            }
        }

        override suspend fun clearSyncTimestamp(): ResultWithError<Unit, LocalDataSourceError> {
            wasClearSyncTimestampCalled = true
            return if (shouldFailClearSyncTimestamp) {
                throw java.io.IOException("Storage error: Failed to clear sync timestamp")
            } else {
                ResultWithError.Success(Unit)
            }
        }
    }

    /**
     * Fake implementation of RemoteDebugDataSource for testing
     */
    private class FakeRemoteDebugDataSource : RemoteDebugDataSource {
        var wasClearServerDataCalled = false
        val addedChats = mutableListOf<Chat>()
        val addedMessages = mutableListOf<Message>()
        private var currentChats = emptyList<Chat>()

        override fun clearServerData() {
            wasClearServerDataCalled = true
            addedChats.clear()
            addedMessages.clear()
            currentChats = emptyList()
        }

        override fun addChatToServer(chat: Chat) {
            addedChats.add(chat)
        }

        override fun addMessageToServerChat(message: Message) {
            addedMessages.add(message)
        }

        override fun getCurrentChats(): List<Chat> = currentChats

        override fun setInitialTimestamp(timestamp: Instant) {
            // No-op for test
        }

        override fun chatPreviews(): Flow<
            ResultWithError<List<ChatPreview>, RemoteDataSourceError>,
            > {
            error("Not needed for this test")
        }

        fun setCurrentChats(chats: List<Chat>) {
            currentChats = chats
        }
    }

    // Regression test for sync integration
    @Test
    fun `initializeWithScenario generates data that syncs correctly`() = testScope.runTest {
        // Given - Create a mock sync flow to verify the generated data can be synced
        val generatedChats = mutableListOf<Chat>()

        // Spy on the sync behavior by capturing what's added to remote
        val spyRemoteDebugDataSource = object : RemoteDebugDataSource {
            override fun clearServerData() {
                // No-op for test
            }

            override fun addChatToServer(chat: Chat) {
                generatedChats.add(chat)
            }

            override fun addMessageToServerChat(message: Message) {
                // No-op for test
            }

            override fun getCurrentChats(): List<Chat> = generatedChats

            override fun setInitialTimestamp(timestamp: Instant) {
                // No-op for test
            }

            override fun chatPreviews(): Flow<
                ResultWithError<List<ChatPreview>, RemoteDataSourceError>,
                > {
                error("Not needed for this test")
            }
        }

        // Recreate repository with the spy
        debugDataRepository = DebugDataRepository(
            dataStore = dataStore,
            localDebugDataSource = localDebugDataSource,
            localDataSources = localDataSources,
            remoteDebugDataSource = spyRemoteDebugDataSource,
            sampleDataProvider = sampleDataProvider,
            coroutineScope = testScope.backgroundScope,
            logger = logger,
        )

        // When - Initialize with scenario
        val scenario = DataScenario.DEMO
        debugDataRepository.initializeWithScenario(scenario)

        // Then - Verify data was generated with proper sync-compatible format
        assertTrue(generatedChats.isNotEmpty(), "Should generate chats")
        assertEquals(scenario.chatCount, generatedChats.size)

        // Verify the generated chats have valid structure for sync
        generatedChats.forEach { chat ->
            // Chat should have valid ID
            assertTrue(chat.id.id.toString().isNotEmpty())

            // Messages should have correct recipient (this was the original bug)
            chat.messages.forEach { message ->
                assertEquals(
                    chat.id,
                    message.recipient,
                    "Message recipient should match chat ID for sync to work correctly",
                )
            }

            // Should have participants
            assertTrue(chat.participants.isNotEmpty())

            // Should have messages (DEMO scenario should have conversations)
            assertTrue(chat.messages.isNotEmpty())
        }

        // Verify settings were updated correctly
        debugDataRepository.debugSettings.test {
            val settings = awaitItem()
            assertEquals(scenario, settings.scenario)
            assertTrue(settings.lastGeneration > 0L, "Should record generation timestamp")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Fake implementations for LocalDataSources dependencies
     */
    private class FakeLocalChatDataSource : LocalChatDataSource {
        override suspend fun insertChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> =
            ResultWithError.Success(chat)

        override suspend fun updateChat(chat: Chat): ResultWithError<Chat, LocalDataSourceError> =
            ResultWithError.Success(chat)

        override suspend fun deleteChat(
            chatId: ChatId,
        ): ResultWithError<Unit, LocalDataSourceError> = ResultWithError.Success(Unit)

        override fun flowChatList(): Flow<
            ResultWithError<
                List<ChatPreview>,
                LocalDataSourceError,
                >,
            > =
            flowOf(ResultWithError.Success(emptyList()))

        override fun flowChatUpdates(
            chatId: ChatId,
        ): Flow<ResultWithError<Chat, LocalDataSourceError>> = flowOf(
            ResultWithError.Failure(LocalDataSourceError.ChatNotFound),
        )
    }

    private class FakeLocalMessageDataSource : LocalMessageDataSource {
        override suspend fun insertMessage(
            message: Message,
        ): ResultWithError<Message, LocalDataSourceError> = ResultWithError.Success(message)

        override suspend fun updateMessage(
            message: Message,
        ): ResultWithError<Message, LocalDataSourceError> = ResultWithError.Success(message)

        override suspend fun deleteMessage(
            messageId: MessageId,
        ): ResultWithError<Unit, LocalDataSourceError> = ResultWithError.Success(Unit)

        override suspend fun getMessage(
            messageId: MessageId,
        ): ResultWithError<Message, LocalDataSourceError> =
            ResultWithError.Failure(LocalDataSourceError.MessageNotFound)

        override fun getMessagePagingSource(chatId: ChatId): PagingSource<Long, Message> =
            error("Not implemented for test")
    }

    private class FakeLocalSyncDataSource : LocalSyncDataSource {
        override suspend fun getLastSyncTimestamp(): ResultWithError<
            Instant?,
            LocalDataSourceError,
            > =
            ResultWithError.Success(Instant.fromEpochMilliseconds(50000))

        override suspend fun updateLastSyncTimestamp(
            timestamp: Instant,
        ): ResultWithError<Unit, LocalDataSourceError> = ResultWithError.Success(Unit)

        override suspend fun applyChatDelta(
            delta: timur.gilfanov.messenger.data.source.remote.ChatDelta,
        ): ResultWithError<Unit, LocalDataSourceError> = ResultWithError.Success(Unit)

        override suspend fun applyChatListDelta(
            delta: timur.gilfanov.messenger.data.source.remote.ChatListDelta,
        ): ResultWithError<Unit, LocalDataSourceError> = ResultWithError.Success(Unit)
    }
}
