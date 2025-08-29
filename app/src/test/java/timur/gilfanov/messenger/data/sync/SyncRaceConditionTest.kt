package timur.gilfanov.messenger.data.sync

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Feature
import timur.gilfanov.messenger.data.source.remote.ChatCreatedDelta
import timur.gilfanov.messenger.data.source.remote.ChatListDelta
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceError
import timur.gilfanov.messenger.data.source.remote.RemoteDataSourceFake
import timur.gilfanov.messenger.data.source.remote.RemoteDebugDataSource
import timur.gilfanov.messenger.debug.DebugTestData
import timur.gilfanov.messenger.domain.entity.ResultWithError

/**
 * Feature-level tests for sync race conditions.
 * These tests reproduce the exact scenarios that caused the original sync issue
 * and verify they work correctly after the fixes.
 */
@Category(Feature::class)
class SyncRaceConditionTest {

    private lateinit var remoteDataSource: RemoteDataSourceFake
    private lateinit var remoteDebugDataSource: RemoteDebugDataSource

    @Before
    fun setup() {
        remoteDataSource = RemoteDataSourceFake()
        remoteDebugDataSource = remoteDataSource
    }

    @Test
    fun `rapid chat additions during active sync flow`() = runTest {
        // Given - Start sync flow to simulate active sync during app startup
        val syncFlow = remoteDataSource.chatsDeltaUpdates(null)

        syncFlow.test {
            // Initial empty state
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(initialResult)
            assertEquals(0, initialResult.data.changes.size)

            // When - Rapidly add multiple chats (simulates debug data generation)
            val chatsToAdd = List(8) { index ->
                DebugTestData.createTestChat(name = "Rapid Chat $index")
            }

            // Add chats concurrently to stress test the race condition fixes
            val addOperations = chatsToAdd.mapIndexed { index, chat ->
                async {
                    delay(index * 10L) // Small delay to create timing variations
                    remoteDebugDataSource.addChatToServer(chat)
                }
            }
            addOperations.awaitAll()

            // Then - All chats should appear in sync deltas without errors
            val receivedChats = mutableSetOf<String>()
            val expectedNames = chatsToAdd.map { it.name }.toSet()
            val maxAttempts = chatsToAdd.size + 2 // Allow a few extra emissions for safety

            // Collect delta updates until we get all chats (or max attempts)
            var attempts = 0
            while (attempts < maxAttempts && !receivedChats.containsAll(expectedNames)) {
                val deltaResult = awaitItem()
                assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(deltaResult)

                val newChatNames = deltaResult.data.changes
                    .filterIsInstance<ChatCreatedDelta>()
                    .map { it.chatMetadata.name }

                receivedChats.addAll(newChatNames)
                attempts++
            }

            // Verify all chats were received
            assertEquals(expectedNames, receivedChats, "Should receive all added chats")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `sync timestamp clearing and data generation race condition`() = runTest {
        // This test reproduces the exact scenario that caused the original issue:
        // 1. Sync starts with old timestamp
        // 2. clearServerData() resets server timestamps to epoch
        // 3. New chats added with epoch+1, epoch+2 timestamps
        // 4. Sync misses the changes because it's using timestamp newer than epoch+X

        // Given - Add some data to establish server timestamps
        val establishingChat = DebugTestData.createTestChat(name = "Establishing Chat")
        remoteDebugDataSource.addChatToServer(establishingChat)

        // Get the current sync point (this simulates the race condition timing)
        val syncTimestamp = remoteDataSource.chatsDeltaUpdates(null).first().let { result ->
            assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(result)
            result.data.toTimestamp
        }

        // When - Clear server data (resets timestamps) and add new data
        remoteDebugDataSource.clearServerData()

        val newChat1 = DebugTestData.createTestChat(name = "Post-Clear Chat 1")
        val newChat2 = DebugTestData.createTestChat(name = "Post-Clear Chat 2")

        remoteDebugDataSource.addChatToServer(newChat1)
        remoteDebugDataSource.addChatToServer(newChat2)

        // Then - Sync from the old timestamp should still see the new chats
        // (This was the bug: sync would see empty delta because new timestamps were older)
        val deltaFromOldTimestamp = remoteDataSource.chatsDeltaUpdates(syncTimestamp)
        deltaFromOldTimestamp.test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(result)

            // Should NOT be empty (this was the original bug)
            assertTrue(
                result.data.changes.isNotEmpty(),
                "Sync should see new chats even after clearServerData reset timestamps",
            )

            val chatNames = result.data.changes
                .filterIsInstance<ChatCreatedDelta>()
                .map { it.chatMetadata.name }
                .toSet()

            assertTrue(chatNames.contains("Post-Clear Chat 1"))
            assertTrue(chatNames.contains("Post-Clear Chat 2"))

            cancelAndIgnoreRemainingEvents()
        }

        // Also verify a fresh sync sees all the data
        val freshSync = remoteDataSource.chatsDeltaUpdates(null)
        freshSync.test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<ChatListDelta, RemoteDataSourceError>>(result)
            assertEquals(2, result.data.changes.size, "Fresh sync should see both new chats")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
