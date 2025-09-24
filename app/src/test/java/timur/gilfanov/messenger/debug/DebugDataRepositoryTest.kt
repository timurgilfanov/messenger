package timur.gilfanov.messenger.debug

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDataSourceFake
import timur.gilfanov.messenger.data.source.remote.AddChatError
import timur.gilfanov.messenger.data.source.remote.AddMessageError
import timur.gilfanov.messenger.data.source.remote.GetChatsError
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteDebugDataSource
import timur.gilfanov.messenger.data.source.remote.RemoteDebugDataSourceError
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.message.Message

@Category(Unit::class)
class DebugDataRepositoryTest {

    private lateinit var dataStore: DebugTestData.FakeDataStore

    private lateinit var localDebugDataSource: LocalDebugDataSourcesDecorator
    private lateinit var remoteDebugDataSource: RemoteDebugDataSourceFakeDecorator
    private lateinit var sampleDataProvider: SampleDataProviderDecorator
    private lateinit var testScope: TestScope
    private lateinit var logger: TrackingTestLogger
    private lateinit var debugDataRepository: DebugDataRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        logger = TrackingTestLogger()
        dataStore = DebugTestData.createTestDataStore()
        remoteDebugDataSource = RemoteDebugDataSourceFakeDecorator(RemoteDebugDataSourceFakeImpl())
        sampleDataProvider = SampleDataProviderDecorator(SampleDataProviderImpl())
        testScope = TestScope(StandardTestDispatcher())
        localDebugDataSource = LocalDebugDataSourcesDecorator(LocalDataSourceFake(logger))

        debugDataRepository = DebugDataRepositoryImpl(
            localDebugDataSource = localDebugDataSource,
            remoteDebugDataSource = remoteDebugDataSource,
            sampleDataProvider = sampleDataProvider,
            coroutineScope = testScope.backgroundScope,
            logger = logger,
        )
    }

    @Test
    fun `debugSettings have correct initial values and all values can be modified`() =
        testScope.runTest {
            // When
            debugDataRepository.settings.test {
                val settings = awaitItem()

                // Then
                assertEquals(DataScenario.STANDARD, settings.scenario)
                assertFalse(settings.autoActivity)
                assertTrue(settings.showNotification)
                assertNull(settings.lastGenerationTimestamp)
            }

            // When
            debugDataRepository.updateSettings { current ->
                current.copy(
                    scenario = DataScenario.DEMO,
                    autoActivity = true,
                    showNotification = false,
                )
            }

            // Then
            debugDataRepository.settings.test {
                val settings = awaitItem()
                assertEquals(DataScenario.DEMO, settings.scenario)
                assertTrue(settings.autoActivity)
                assertFalse(settings.showNotification)
            }
        }

    @Test
    fun `initializeWithScenario clears data and generates new data`() = testScope.runTest {
        // Given
        val scenario = DataScenario.MINIMAL

        // When
        debugDataRepository.initializeWithScenario(scenario)

        // Then - Verify data generation - check that chats were added to remote
        val chats = remoteDebugDataSource.getChats()
        assertIs<ResultWithError.Success<ImmutableList<Chat>, GetChatsError>>(chats)
        assertEquals(scenario.chatCount, chats.data.size)

        // Verify settings update
        debugDataRepository.settings.test {
            val settings = awaitItem()
            assertEquals(scenario, settings.scenario)
            assertNotNull(settings.lastGenerationTimestamp)
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
        val chats = remoteDebugDataSource.getChats()
        assertIs<ResultWithError.Success<ImmutableList<Chat>, GetChatsError>>(chats)
        assertEquals(DataScenario.EDGE_CASES.chatCount, chats.data.size)
    }

    @Test
    fun `clearAllData clears both local and remote data`() = testScope.runTest {
        // When
        debugDataRepository.clearData()

        // Then
        val localChats = localDebugDataSource.flowChatList().first()
        assertIs<ResultWithError.Success<List<ChatPreview>, LocalDataSourceError>>(localChats)
        assertEquals(0, localChats.data.size)
        val chats = remoteDebugDataSource.getChats()
        assertIs<ResultWithError.Success<ImmutableList<Chat>, GetChatsError>>(chats)
        assertEquals(0, chats.data.size)
    }

    @Test
    fun `getScenario returns scenario from preferences`() = testScope.runTest {
        // Given - Set a scenario in preferences
        debugDataRepository.updateSettings { current ->
            current.copy(scenario = DataScenario.HEAVY)
        }

        // When
        val settings = debugDataRepository.getSettings()
        assertIs<ResultWithError.Success<DebugSettings, GetSettingsError>>(settings)
        val savedScenario = settings.data.scenario

        // Then
        assertEquals(DataScenario.HEAVY, savedScenario)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `auto activity add messages when enabled via settings`() = testScope.runTest {
        // Given - Setup chats for simulation
        val testChat = DebugTestData.createTestChat()
        remoteDebugDataSource.addChat(testChat)
        val initialMessageCount = remoteDebugDataSource.getMessagesSize()

        // When - Enable auto activity
        debugDataRepository.updateSettings { settings -> settings.copy(autoActivity = true) }

        // Then - Verify the setting is enabled
        debugDataRepository.settings.test {
            val settings = awaitItem()
            assertTrue(settings.autoActivity, "Auto activity should be enabled")
        }

        remoteDebugDataSource.messageAddedCount.test {
            val initial = awaitItem()
            assertEquals(0, initial)
            assertEquals(initialMessageCount, remoteDebugDataSource.getMessagesSize())

            val first = awaitItem()
            assertEquals(1, first)
            assertEquals(initialMessageCount + 1, remoteDebugDataSource.getMessagesSize())

            val second = awaitItem()
            assertEquals(2, second)
            assertEquals(initialMessageCount + 2, remoteDebugDataSource.getMessagesSize())
        }
    }

    @Test
    fun `handles clear data error in data regeneration`() = testScope.runTest {
        // Given - Simulate error by making local data source fail
        localDebugDataSource.debugDeleteAllChatsError = LocalDataSourceError.StorageUnavailable
        val localChats = localDebugDataSource.flowChatList().first()
        val remoteChats = remoteDebugDataSource.getChats()

        // When - Initialize should still complete
        val result = debugDataRepository.regenerateData()

        // Then - Should handle error gracefully
        assertIs<ResultWithError.Failure<Unit, RegenerateDataError>>(result)
        assertNotNull(result.error.clearData)
        assertEquals(1, result.error.clearData.failedOperations.size)
        assertTrue(result.error.clearData.partialSuccess)
        assertEquals(
            "deleteAllChats",
            result.error.clearData.failedOperations[0].first,
        )
        assertEquals(
            "StorageUnavailable",
            result.error.clearData.failedOperations[0].second,
        )

        // Data should still be the same
        assertEquals(localChats, localDebugDataSource.flowChatList().first())
        assertEquals(remoteChats, remoteDebugDataSource.getChats())
    }

    @Test
    fun `handles clear messages error in data regeneration`() = testScope.runTest {
        // Given - Simulate error by making local data source fail deleteAllMessages
        localDebugDataSource.debugDeleteAllMessagesError = LocalDataSourceError.StorageUnavailable
        val localChats = localDebugDataSource.flowChatList().first()
        val remoteChats = remoteDebugDataSource.getChats()

        // When - Initialize should still complete
        val result = debugDataRepository.regenerateData()

        // Then - Should handle error gracefully
        assertIs<ResultWithError.Failure<Unit, RegenerateDataError>>(result)
        assertNotNull(result.error.clearData)
        assertEquals(1, result.error.clearData.failedOperations.size)
        assertTrue(result.error.clearData.partialSuccess)
        assertEquals(
            "deleteAllMessages",
            result.error.clearData.failedOperations[0].first,
        )
        assertEquals(
            "StorageUnavailable",
            result.error.clearData.failedOperations[0].second,
        )

        // Data should still be the same
        assertEquals(localChats, localDebugDataSource.flowChatList().first())
        assertEquals(remoteChats, remoteDebugDataSource.getChats())
    }

    @Test
    fun `handles clear sync timestamp error in data regeneration`() = testScope.runTest {
        // Given - Simulate error by making local data source fail clearSyncTimestamp
        localDebugDataSource.debugClearSyncTimestampError = LocalDataSourceError.StorageUnavailable
        val localChats = localDebugDataSource.flowChatList().first()
        val remoteChats = remoteDebugDataSource.getChats()

        // When - Initialize should still complete
        val result = debugDataRepository.regenerateData()

        // Then - Should handle error gracefully
        assertIs<ResultWithError.Failure<Unit, RegenerateDataError>>(result)
        assertNotNull(result.error.clearData)
        assertEquals(1, result.error.clearData.failedOperations.size)
        assertTrue(result.error.clearData.partialSuccess)
        assertEquals(
            "clearSyncTimestamp",
            result.error.clearData.failedOperations[0].first,
        )
        assertEquals(
            "StorageUnavailable",
            result.error.clearData.failedOperations[0].second,
        )

        // Data should still be the same
        assertEquals(localChats, localDebugDataSource.flowChatList().first())
        assertEquals(remoteChats, remoteDebugDataSource.getChats())
    }

    @Test
    fun `handles generation error in data regeneration`() = testScope.runTest {
        // Given - Simulate error by making local data source fail
        val initialTimestamp = debugDataRepository.settings.first().lastGenerationTimestamp
        sampleDataProvider.generateEmptyChats = true

        // When - Initialize should still complete
        val result = debugDataRepository.regenerateData()

        // Then - Should handle error gracefully
        assertIs<ResultWithError.Failure<Unit, RegenerateDataError>>(result)
        assertNotNull(result.error.generateData)
        assertEquals(GenerateDataError.NoChatsGenerated, result.error.generateData)

        // Data should still be the same
        assertEquals(
            initialTimestamp,
            debugDataRepository.settings.first().lastGenerationTimestamp,
        )
    }

    @Test
    fun `handles data population error in data regeneration`() = testScope.runTest {
        // Given - Simulate error by making remote add chat fail
        val initialTimestamp = debugDataRepository.settings.first().lastGenerationTimestamp
        val error = AddChatError.RemoteError(
            error = RemoteDebugDataSourceError.CooldownActive(remaining = 1.minutes),
        )
        remoteDebugDataSource.remoteAddChatError = error

        // When - Initialize should fail due to population error
        val result = debugDataRepository.regenerateData()

        // Then - Should handle error gracefully
        assertIs<ResultWithError.Failure<Unit, RegenerateDataError>>(result)
        assertNotNull(result.error.populateRemote)
        assertTrue(result.error.populateRemote.addChatError.isNotEmpty())

        // Data should still be the same
        assertEquals(
            initialTimestamp,
            debugDataRepository.settings.first().lastGenerationTimestamp,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `auto activity stops when disabled`() = testScope.runTest {
        // Given - Auto activity is running
        val testChat = DebugTestData.createTestChat()
        remoteDebugDataSource.addChat(testChat)

        debugDataRepository.updateSettings { settings -> settings.copy(autoActivity = true) }
        advanceTimeBy(6000L) // Trigger first message

        val initialMessageCount = remoteDebugDataSource.getMessagesSize()

        // When - Disable auto activity
        debugDataRepository.updateSettings { settings -> settings.copy(autoActivity = false) }
        advanceTimeBy(10000L)

        // Then - No more messages should be generated after disabling
        assertEquals(initialMessageCount, remoteDebugDataSource.getMessagesSize())
    }

    @Test
    fun `regenerateData updates last generation timestamp`() = testScope.runTest {
        // Given - Get initial timestamp
        debugDataRepository.regenerateData()
        val initialLastGeneration = debugDataRepository.settings.first().lastGenerationTimestamp
        assertNotNull(initialLastGeneration)

        // When
        debugDataRepository.regenerateData()

        // Then
        debugDataRepository.settings.test {
            val settings = awaitItem()
            assertTrue(
                settings.lastGenerationTimestamp != initialLastGeneration,
                "Last generation should be updated after regenerating data",
            )
        }
    }

    // Regression test for sync integration
    @Test
    fun `initializeWithScenario add chats to remote data source with chat ID as recepient`() =
        testScope.runTest {
            // Given - Create a mock sync flow to verify the generated data can be synced
            val generatedChats = mutableListOf<Chat>()

            // Spy on the sync behavior by capturing what's added to remote
            val spyRemoteDebugDataSource = object : RemoteDebugDataSource {
                override fun clearData() {
                    // No-op for test
                }

                override fun addChat(chat: Chat): ResultWithError<Unit, AddChatError> {
                    generatedChats.add(chat)
                    return ResultWithError.Success(Unit)
                }

                override fun addMessage(message: Message): ResultWithError<Unit, AddMessageError> {
                    error("Not needed for this test")
                }

                override fun getChats(): ResultWithError<ImmutableList<Chat>, GetChatsError> {
                    error("Not needed for this test")
                }

                override fun getMessagesSize(): Int {
                    error("Not needed for this test")
                }

                override val chatPreviews:
                    Flow<ResultWithError<List<ChatPreview>, RemoteDataSourceError>> = emptyFlow()
            }

            // Recreate repository with the spy
            debugDataRepository = DebugDataRepositoryImpl(
                localDebugDataSource = localDebugDataSource,
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
            debugDataRepository.settings.test {
                val settings = awaitItem()
                assertEquals(scenario, settings.scenario)
                assertNotNull(settings.lastGenerationTimestamp) {
                    "Should record generation timestamp"
                }
            }
        }
}

/**
 * Fake implementation of RemoteDebugDataSource for testing
 */
private class RemoteDebugDataSourceFakeImpl : RemoteDebugDataSourceFake {
    private val chats = mutableListOf<Chat>()
    private val messages = mutableListOf<Message>()

    override val messageAddedCount = MutableStateFlow(0)

    override fun clearData() {
        chats.clear()
        messages.clear()
    }

    override fun addChat(chat: Chat): ResultWithError<Unit, AddChatError> {
        chats.add(chat)
        return ResultWithError.Success(Unit)
    }

    override fun addMessage(message: Message): ResultWithError<Unit, AddMessageError> {
        messages.add(message)
        messageAddedCount.update { it + 1 }
        return ResultWithError.Success(Unit)
    }

    override fun getChats(): ResultWithError<ImmutableList<Chat>, GetChatsError> =
        ResultWithError.Success(chats.toImmutableList())

    override fun getMessagesSize(): Int = messages.size

    override val chatPreviews: Flow<ResultWithError<List<ChatPreview>, RemoteDataSourceError>> =
        emptyFlow()
}

interface RemoteDebugDataSourceFake : RemoteDebugDataSource {

    val messageAddedCount: MutableStateFlow<Int>
}
