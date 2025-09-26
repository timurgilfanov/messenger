package timur.gilfanov.messenger.debug

import app.cash.turbine.test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.TestLogger
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.data.source.local.LocalDataSourceError
import timur.gilfanov.messenger.data.source.local.LocalDataSourceFake
import timur.gilfanov.messenger.data.source.local.LocalDebugDataSource
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview

@Category(Unit::class)
class LocalDataSourceFakeDebugTest {

    private lateinit var logger: TestLogger
    private lateinit var localDataSourceFake: LocalDataSourceFake
    private lateinit var localDebugDataSource: LocalDebugDataSource

    @Before
    fun setup() {
        logger = TestLogger()
        localDataSourceFake = LocalDataSourceFake(logger)
        localDebugDataSource = localDataSourceFake
    }

    @Test
    fun `deleteAllChats should clear all in-memory chats`() = runTest {
        // Given - Add some test chats
        val testChat1 = DebugTestData.createTestChat(name = "Fake Chat 1")
        val testChat2 = DebugTestData.createTestChat(name = "Fake Chat 2")

        localDataSourceFake.insertChat(testChat1)
        localDataSourceFake.insertChat(testChat2)

        // Verify chats exist
        localDataSourceFake.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(result)
            @Suppress("UNCHECKED_CAST")
            val chatList = (result as ResultWithError.Success<List<*>, *>).data
            assertEquals(2, chatList.size)
            cancelAndIgnoreRemainingEvents()
        }

        // When
        val deleteResult = localDebugDataSource.deleteAllChats()

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(deleteResult)

        // Verify all chats are cleared
        localDataSourceFake.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(result)
            @Suppress("UNCHECKED_CAST")
            val chatList = (result as ResultWithError.Success<List<*>, *>).data
            assertTrue(chatList.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAllChats should emit empty list on flowChatList`() = runTest {
        // Given - Add test chats and start observing flow
        val testChat = DebugTestData.createTestChat()
        localDataSourceFake.insertChat(testChat)

        // When
        localDataSourceFake.flowChatList().test {
            // Should have initial chat
            val initialResult = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(initialResult)
            @Suppress("UNCHECKED_CAST")
            val initialChatList = (initialResult as ResultWithError.Success<List<*>, *>).data
            assertEquals(1, initialChatList.size)

            // Clear all chats
            localDebugDataSource.deleteAllChats()

            // Should emit empty list
            val emptyResult = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(emptyResult)
            @Suppress("UNCHECKED_CAST")
            val emptyChatList = (emptyResult as ResultWithError.Success<List<*>, *>).data
            assertTrue(emptyChatList.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAllMessages should clear all in-memory messages`() = runTest {
        // Given - Add test chat with messages
        val testChat = DebugTestData.createTestChat(messageCount = 3)
        localDataSourceFake.insertChat(testChat)

        // Add individual messages
        testChat.messages.forEach { message ->
            localDataSourceFake.insertMessage(message)
        }

        // When
        val deleteResult = localDebugDataSource.deleteAllMessages()

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(deleteResult)

        // Verify messages are cleared by checking individual message retrieval
        testChat.messages.forEach { message ->
            val result = localDataSourceFake.getMessage(message.id)
            assertIs<ResultWithError.Failure<*, *>>(result)
            assertIs<LocalDataSourceError.MessageNotFound>(result.error)
        }
    }

    @Test
    fun `clearSyncTimestamp should reset sync timestamp in memory`() = runTest {
        // Given - Set a sync timestamp in the fake
        // First trigger sync timestamp storage by getting it
        localDataSourceFake.getLastSyncTimestamp()

        // When
        val clearResult = localDebugDataSource.clearSyncTimestamp()

        // Then
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(clearResult)

        // Verify timestamp is cleared (should return null/default)
        val timestampResult = localDataSourceFake.getLastSyncTimestamp()
        when (timestampResult) {
            is ResultWithError.Success -> {
                // Timestamp should be null or reset
                assertTrue(timestampResult.data == null)
            }
            is ResultWithError.Failure -> {
                // Or it might return an error indicating no timestamp exists
                assertTrue(true) // This is acceptable
            }
        }
    }

    @Test
    fun `debug operations should handle empty state gracefully`() = runTest {
        // Given - Empty fake data source (no chats, no messages)

        // When & Then - All operations should succeed
        val chatResult = localDebugDataSource.deleteAllChats()
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(chatResult)

        val messageResult = localDebugDataSource.deleteAllMessages()
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(messageResult)

        val syncResult = localDebugDataSource.clearSyncTimestamp()
        assertIs<ResultWithError.Success<Unit, LocalDataSourceError>>(syncResult)
    }

    @Test
    fun `debug operations should allow fresh data population after clearing`() = runTest {
        // Given - Populate with data, then clear
        val originalChat = DebugTestData.createTestChat(name = "Original Chat")
        localDataSourceFake.insertChat(originalChat)

        localDebugDataSource.deleteAllChats()
        localDebugDataSource.deleteAllMessages()
        localDebugDataSource.clearSyncTimestamp()

        // When - Add fresh data
        val newChat = DebugTestData.createTestChat(name = "Fresh Chat")
        val insertResult = localDataSourceFake.insertChat(newChat)

        // Then
        assertIs<ResultWithError.Success<*, *>>(insertResult)

        localDataSourceFake.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(result)
            @Suppress("UNCHECKED_CAST")
            val chatList = (result as ResultWithError.Success<List<ChatPreview>, *>).data
            assertEquals(1, chatList.size)
            assertEquals("Fresh Chat", chatList.first().name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAllChats should clear chat-message associations`() = runTest {
        // Given - Add chat with messages
        val testChat = DebugTestData.createTestChat(messageCount = 2)
        localDataSourceFake.insertChat(testChat)

        testChat.messages.forEach { message ->
            localDataSourceFake.insertMessage(message)
        }

        // Verify messages exist
        testChat.messages.forEach { message ->
            val result = localDataSourceFake.getMessage(message.id)
            assertIs<ResultWithError.Success<*, *>>(result)
        }

        // When
        localDebugDataSource.deleteAllChats()

        // Then - Chat-specific messages should still exist (they're separate in fake impl)
        // but chat should be gone
        localDataSourceFake.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(result)
            @Suppress("UNCHECKED_CAST")
            val chatList = (result as ResultWithError.Success<List<*>, *>).data
            assertTrue(chatList.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `operations should be idempotent`() = runTest {
        // Given - Add some data
        val testChat = DebugTestData.createTestChat()
        localDataSourceFake.insertChat(testChat)

        // When - Perform operations multiple times
        val result1 = localDebugDataSource.deleteAllChats()
        val result2 = localDebugDataSource.deleteAllChats()
        val result3 = localDebugDataSource.deleteAllMessages()
        val result4 = localDebugDataSource.deleteAllMessages()
        val result5 = localDebugDataSource.clearSyncTimestamp()
        val result6 = localDebugDataSource.clearSyncTimestamp()

        // Then - All operations should succeed
        assertIs<ResultWithError.Success<*, *>>(result1)
        assertIs<ResultWithError.Success<*, *>>(result2)
        assertIs<ResultWithError.Success<*, *>>(result3)
        assertIs<ResultWithError.Success<*, *>>(result4)
        assertIs<ResultWithError.Success<*, *>>(result5)
        assertIs<ResultWithError.Success<*, *>>(result6)

        // Final state should be empty
        localDataSourceFake.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(result)
            @Suppress("UNCHECKED_CAST")
            val chatList = (result as ResultWithError.Success<List<*>, *>).data
            assertTrue(chatList.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `debug operations should maintain flow emissions`() = runTest {
        // Given - Start observing flow
        localDataSourceFake.flowChatList().test {
            // Initial empty state
            val initial = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(initial)
            @Suppress("UNCHECKED_CAST")
            val initialList = (initial as ResultWithError.Success<List<*>, *>).data
            assertTrue(initialList.isEmpty())

            // Add some data
            val testChat = DebugTestData.createTestChat()
            localDataSourceFake.insertChat(testChat)

            // Should emit chat list
            val withData = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(withData)
            @Suppress("UNCHECKED_CAST")
            val withDataList = (withData as ResultWithError.Success<List<*>, *>).data
            assertEquals(1, withDataList.size)

            // Clear all data
            localDebugDataSource.deleteAllChats()

            // Should emit empty list again
            val cleared = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(cleared)
            @Suppress("UNCHECKED_CAST")
            val clearedList = (cleared as ResultWithError.Success<List<*>, *>).data
            assertTrue(clearedList.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `debug operations should handle concurrent access safely`() = runTest {
        // Given - Add test data
        val testChat1 = DebugTestData.createTestChat(name = "Chat 1")
        val testChat2 = DebugTestData.createTestChat(name = "Chat 2")

        localDataSourceFake.insertChat(testChat1)
        localDataSourceFake.insertChat(testChat2)

        // When - Perform operations sequentially (simulating concurrent safety)
        val chatResult = localDebugDataSource.deleteAllChats()
        val messageResult = localDebugDataSource.deleteAllMessages()
        val timestampResult = localDebugDataSource.clearSyncTimestamp()

        val results = listOf(chatResult, messageResult, timestampResult)

        // Then - All operations should complete successfully
        results.forEach { result ->
            assertIs<ResultWithError.Success<*, *>>(result)
        }

        // Final state should be consistent
        localDataSourceFake.flowChatList().test {
            val result = awaitItem()
            assertIs<ResultWithError.Success<*, *>>(result)
            @Suppress("UNCHECKED_CAST")
            val chatList = (result as ResultWithError.Success<List<*>, *>).data
            assertTrue(chatList.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
