package timur.gilfanov.messenger.debug

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.data.source.remote.ChatCreatedDelta
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteDebugDataSource
import timur.gilfanov.messenger.domain.entity.ResultWithError

@Category(Unit::class)
class RemoteDataSourceFakeDebugTest {

    private lateinit var remoteDataSourceFake: RemoteDataSourceFake
    private lateinit var remoteDebugDataSource: RemoteDebugDataSource

    @Before
    fun setup() {
        remoteDataSourceFake = RemoteDataSourceFake()
        remoteDebugDataSource = remoteDataSourceFake
    }

    @Test
    fun `clearData should remove all server chats`() = runTest {
        // Given - Add some test chats to server
        val testChat1 = DebugTestData.createTestChat(name = "Server Chat 1")
        val testChat2 = DebugTestData.createTestChat(name = "Server Chat 2")

        remoteDebugDataSource.addChat(testChat1)
        remoteDebugDataSource.addChat(testChat2)

        // Verify chats exist by checking delta updates
        val initialDelta = remoteDataSourceFake.chatsDeltaUpdates(null)
        initialDelta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            assertEquals(2, deltaResult.data.changes.size)
            cancelAndIgnoreRemainingEvents()
        }

        // When
        remoteDebugDataSource.clearData()

        // Then - Verify all data is cleared
        val afterClearDelta = remoteDataSourceFake.chatsDeltaUpdates(null)
        afterClearDelta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            assertEquals(0, deltaResult.data.changes.size)
            // Changes is now a list of different delta types, check total count
            // Total changes should be 0 after clearing
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearData should advance server timestamp to prevent sync issues`() = runTest {
        // Given - Add a chat to advance server timestamp
        val testChat = DebugTestData.createTestChat()
        remoteDebugDataSource.addChat(testChat)

        // Get initial timestamp (should be > 0)
        val initialDelta = remoteDataSourceFake.chatsDeltaUpdates(null)
        var initialTimestamp: Instant? = null
        initialDelta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            initialTimestamp = deltaResult.data.toTimestamp
            assertTrue(initialTimestamp > Instant.fromEpochMilliseconds(0))
            cancelAndIgnoreRemainingEvents()
        }

        // When
        remoteDebugDataSource.clearData()

        // Then - Add a new chat and verify timestamp advances to prevent sync race conditions
        val newTestChat = DebugTestData.createTestChat(name = "New Chat")
        remoteDebugDataSource.addChat(newTestChat)

        val afterClearDelta = remoteDataSourceFake.chatsDeltaUpdates(null)
        afterClearDelta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            // Timestamp should advance forward to prevent sync race conditions
            assertTrue(deltaResult.data.toTimestamp > initialTimestamp!!)
            assertEquals(1, deltaResult.data.changes.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearData should allow fresh data population`() = runTest {
        // Given - Populate server with data, then clear
        val originalChat = DebugTestData.createTestChat(name = "Original Chat")
        remoteDebugDataSource.addChat(originalChat)
        remoteDebugDataSource.clearData()

        // When - Add new data after clearing
        val newChat1 = DebugTestData.createTestChat(name = "New Chat 1")
        val newChat2 = DebugTestData.createTestChat(name = "New Chat 2")

        remoteDebugDataSource.addChat(newChat1)
        remoteDebugDataSource.addChat(newChat2)

        // Then - Only new data should exist
        val delta = remoteDataSourceFake.chatsDeltaUpdates(null)
        delta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            assertEquals(2, deltaResult.data.changes.size)

            val createdChatNames = deltaResult.data.changes.filterIsInstance<ChatCreatedDelta>()
                .map { it.chatMetadata.name }.toSet()
            assertTrue(createdChatNames.contains("New Chat 1"))
            assertTrue(createdChatNames.contains("New Chat 2"))
            assertTrue(!createdChatNames.contains("Original Chat"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearData should handle empty server gracefully`() = runTest {
        // Given - Empty server (no chats added)

        // When
        remoteDebugDataSource.clearData()

        // Then - Should not cause any errors
        val delta = remoteDataSourceFake.chatsDeltaUpdates(null)
        delta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            assertEquals(0, deltaResult.data.changes.size)
            // Changes is now a list of different delta types, check total count
            // Total changes should be 0 after clearing
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearData should clear operation timestamps but advance timestamp`() = runTest {
        // Given - Perform some operations to create timestamp history
        val testChat1 = DebugTestData.createTestChat(name = "Chat 1")
        val testChat2 = DebugTestData.createTestChat(name = "Chat 2")
        remoteDebugDataSource.addChat(testChat1)
        remoteDebugDataSource.addChat(testChat2)

        // Get timestamp before clearing to compare
        val beforeClearDelta = remoteDataSourceFake.chatsDeltaUpdates(null)
        var beforeClearTimestamp: Instant? = null
        beforeClearDelta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            beforeClearTimestamp = deltaResult.data.toTimestamp
            assertEquals(2, deltaResult.data.changes.size) // Should have both chats
            cancelAndIgnoreRemainingEvents()
        }

        // When
        remoteDebugDataSource.clearData()

        // Then - Operations should be cleared but timestamp should advance
        val newTestChat = DebugTestData.createTestChat(name = "Post-Clear Chat")
        remoteDebugDataSource.addChat(newTestChat)

        val delta = remoteDataSourceFake.chatsDeltaUpdates(null)
        delta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            // Should advance beyond the timestamp before clear to prevent sync race conditions
            assertTrue(deltaResult.data.toTimestamp > beforeClearTimestamp!!)
            assertEquals(1, deltaResult.data.changes.size) // Only the new chat
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearData should be idempotent`() = runTest {
        // Given - Add data and clear once
        val testChat = DebugTestData.createTestChat()
        remoteDebugDataSource.addChat(testChat)
        remoteDebugDataSource.clearData()

        // When - Clear again
        remoteDebugDataSource.clearData()

        // Then - Should not cause errors
        val delta = remoteDataSourceFake.chatsDeltaUpdates(null)
        delta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            assertEquals(0, deltaResult.data.changes.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearData should clear server state for consistent testing`() = runTest {
        // Given - Multiple test scenarios with different data
        for (i in 1..5) {
            val chat = DebugTestData.createTestChat(name = "Scenario $i Chat")
            remoteDebugDataSource.addChat(chat)
        }

        // When - Clear between scenarios
        remoteDebugDataSource.clearData()

        // Then - Server should be in consistent empty state
        val delta = remoteDataSourceFake.chatsDeltaUpdates(null)
        delta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            assertTrue(deltaResult.data.changes.isEmpty())
            // All data should be cleared
            // Timestamp advances deterministically (5 operations + 1 for clear = 6s)
            assertEquals(Instant.fromEpochMilliseconds(6000), deltaResult.data.toTimestamp)
            cancelAndIgnoreRemainingEvents()
        }

        // Verify we can populate fresh data consistently
        val newChat = DebugTestData.createTestChat(name = "Fresh Start")
        remoteDebugDataSource.addChat(newChat)

        val freshDelta = remoteDataSourceFake.chatsDeltaUpdates(null)
        freshDelta.test {
            val deltaResult = awaitItem()
            assertTrue(deltaResult is ResultWithError.Success)
            assertEquals(1, deltaResult.data.changes.size)
            val createdDelta = deltaResult.data.changes.filterIsInstance<ChatCreatedDelta>().first()
            assertEquals("Fresh Start", createdDelta.chatMetadata.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Regression tests for debug data generation with sync
    @Test
    fun `debug data generation with sync running concurrently`() = runTest {
        // Given - Start with empty state
        val initialDelta = remoteDataSourceFake.chatsDeltaUpdates(null)
        initialDelta.test {
            val initialResult = awaitItem()
            assertTrue(initialResult is ResultWithError.Success)
            assertEquals(0, initialResult.data.changes.size)
            cancelAndIgnoreRemainingEvents()
        }

        // When - Generate debug data (simulates DebugDataRepository.regenerateData)
        remoteDebugDataSource.clearData()
        val debugChat1 = DebugTestData.createTestChat(name = "Debug Chat 1")
        val debugChat2 = DebugTestData.createTestChat(name = "Debug Chat 2")

        remoteDebugDataSource.addChat(debugChat1)
        remoteDebugDataSource.addChat(debugChat2)

        // Then - Sync should receive the new chats without errors
        val afterGenerationDelta = remoteDataSourceFake.chatsDeltaUpdates(null)
        afterGenerationDelta.test {
            val result = awaitItem()
            assertTrue(result is ResultWithError.Success)

            // Should have both chats with proper forward timestamps
            assertEquals(2, result.data.changes.size)
            val chatNames = result.data.changes.filterIsInstance<ChatCreatedDelta>()
                .map { it.chatMetadata.name }.toSet()

            assertTrue(chatNames.contains("Debug Chat 1"))
            assertTrue(chatNames.contains("Debug Chat 2"))

            // Verify timestamps are deterministic and forward-progressing
            assertTrue(result.data.toTimestamp > Instant.fromEpochMilliseconds(0))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearData followed by addChat creates valid delta`() = runTest {
        // Given - Add some initial data to establish a baseline timestamp
        val initialChat = DebugTestData.createTestChat(name = "Initial Chat")
        remoteDebugDataSource.addChat(initialChat)

        // Get the timestamp before clearing for comparison
        val beforeClearDelta = remoteDataSourceFake.chatsDeltaUpdates(null)
        var beforeClearTimestamp: Instant? = null
        beforeClearDelta.test {
            val result = awaitItem()
            assertTrue(result is ResultWithError.Success)
            beforeClearTimestamp = result.data.toTimestamp
            cancelAndIgnoreRemainingEvents()
        }

        // When - Clear server data and immediately add new chat (debug data pattern)
        remoteDebugDataSource.clearData()
        val newChat = DebugTestData.createTestChat(name = "Post-Clear Chat")
        remoteDebugDataSource.addChat(newChat)

        // Then - Delta should have valid timestamp newer than before clear
        remoteDataSourceFake.chatsDeltaUpdates(null).test {
            val result = awaitItem()
            assertTrue(result is ResultWithError.Success)

            val delta = result.data
            assertEquals(1, delta.changes.size)

            val chatDelta = delta.changes.filterIsInstance<ChatCreatedDelta>().first()
            assertEquals("Post-Clear Chat", chatDelta.chatMetadata.name)

            // Most importantly: timestamp should advance beyond the pre-clear timestamp
            // This prevents the sync issue where old timestamps caused empty deltas
            assertTrue(
                chatDelta.timestamp > beforeClearTimestamp!!,
                "Chat timestamp ${chatDelta.timestamp} should be newer than $beforeClearTimestamp",
            )

            cancelAndIgnoreRemainingEvents()
        }
    }
}
