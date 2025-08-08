package timur.gilfanov.messenger.data.source.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import timur.gilfanov.annotations.Component
import timur.gilfanov.messenger.domain.entity.ResultWithError
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
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
            participantDao = databaseRule.participantDao,
            database = databaseRule.database,
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

    private fun createTestChat(
        id: String = "66666666-6666-6666-6666-666666666666", // Fixed UUID
        name: String = "Test Chat",
    ) = Chat(
        id = ChatId(UUID.fromString(id)),
        name = name,
        participants = persistentSetOf(
            Participant(
                id = ParticipantId(UUID.fromString("44444444-4444-4444-4444-444444444444")),
                name = "User 1",
                pictureUrl = null,
                joinedAt = Instant.fromEpochMilliseconds(1000000),
                onlineAt = null,
                isAdmin = false,
                isModerator = false,
            ),
            Participant(
                id = ParticipantId(UUID.fromString("55555555-5555-5555-5555-555555555555")),
                name = "User 2",
                pictureUrl = null,
                joinedAt = Instant.fromEpochMilliseconds(1100000),
                onlineAt = null,
                isAdmin = true,
                isModerator = false,
            ),
        ),
        pictureUrl = null,
        messages = persistentListOf(),
        rules = persistentSetOf(),
        unreadMessagesCount = 0,
        lastReadMessageId = null,
    )
}
