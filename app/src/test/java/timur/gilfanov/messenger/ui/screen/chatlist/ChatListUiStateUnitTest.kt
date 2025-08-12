package timur.gilfanov.messenger.ui.screen.chatlist

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.junit.experimental.categories.Category
import timur.gilfanov.messenger.annotations.Unit
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.testutil.DomainTestFixtures

@Category(Unit::class)
class ChatListUiStateUnitTest {

    private val testChatId = ChatId(UUID.randomUUID())
    private val testUserId = ParticipantId(UUID.randomUUID())
    private val testOtherUserId = ParticipantId(UUID.randomUUID())
    private val testTimestamp = Clock.System.now()

    private fun createTestParticipant(
        id: ParticipantId = testUserId,
        name: String = "Test User",
        onlineAt: Instant? = null,
    ) = DomainTestFixtures.createTestParticipant(
        id = id,
        name = name,
        joinedAt = testTimestamp,
        onlineAt = onlineAt,
    )

    private fun createTestTextMessage(
        text: String = "Test message",
        senderId: ParticipantId = testUserId,
        createdAt: Instant = testTimestamp,
    ) = DomainTestFixtures.createTestTextMessage(
        text = text,
        sender = createTestParticipant(senderId),
        createdAt = createdAt,
    )

    private data class TestChatParams(
        val id: ChatId = ChatId(UUID.randomUUID()),
        val name: String = "Test Chat",
        val pictureUrl: String? = null,
        val messages: List<Message> = emptyList(),
        val participants: List<Participant> = emptyList(),
        val isOneToOne: Boolean = true,
        val unreadMessagesCount: Int = 0,
    )

    private fun createTestChat(params: TestChatParams = TestChatParams()) =
        DomainTestFixtures.createTestChat(
            id = params.id,
            name = params.name,
            pictureUrl = params.pictureUrl,
            messages = params.messages,
            participants = params.participants.toSet(),
            isOneToOne = params.isOneToOne,
            unreadMessagesCount = params.unreadMessagesCount,
        )

    @Test
    fun `toChatListItemUiModel maps basic chat properties correctly`() {
        val chat = createTestChat(
            TestChatParams(
                id = testChatId,
                name = "Test Chat",
                pictureUrl = "https://example.com/picture.jpg",
                participants = listOf(
                    createTestParticipant(testUserId),
                    createTestParticipant(testOtherUserId),
                ),
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertEquals(testChatId, result.id)
        assertEquals("Test Chat", result.name)
        assertEquals("https://example.com/picture.jpg", result.pictureUrl)
    }

    @Test
    fun `toChatListItemUiModel maps last text message correctly`() {
        val lastMessage = createTestTextMessage(text = "Last message")
        val chat = createTestChat(
            TestChatParams(
                messages = listOf(lastMessage),
                participants = listOf(
                    createTestParticipant(testUserId),
                    createTestParticipant(testOtherUserId),
                ),
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertEquals("Last message", result.lastMessage)
        assertEquals(testTimestamp, result.lastMessageTime)
    }

    @Test
    fun `toChatListItemUiModel handles chat with no messages`() {
        val chat = createTestChat(
            TestChatParams(
                messages = emptyList(),
                participants = listOf(
                    createTestParticipant(testUserId),
                    createTestParticipant(testOtherUserId),
                ),
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertNull(result.lastMessage)
        assertNull(result.lastMessageTime)
    }

    @Test
    fun `toChatListItemUiModel maps unread count correctly`() {
        val chat = createTestChat(
            TestChatParams(
                unreadMessagesCount = 5,
                participants = listOf(
                    createTestParticipant(testUserId),
                    createTestParticipant(testOtherUserId),
                ),
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertEquals(5, result.unreadCount)
    }

    @Test
    fun `toChatListItemUiModel maps online status for one-to-one chat`() {
        val onlineParticipant = createTestParticipant(
            id = testOtherUserId,
            onlineAt = testTimestamp,
        )
        val chat = createTestChat(
            TestChatParams(
                participants = listOf(
                    createTestParticipant(testUserId),
                    onlineParticipant,
                ),
                isOneToOne = true,
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertEquals(true, result.isOnline)
        assertEquals(testTimestamp, result.lastOnlineTime)
    }

    @Test
    fun `toChatListItemUiModel maps offline status for one-to-one chat`() {
        val offlineParticipant = createTestParticipant(
            id = testOtherUserId,
            onlineAt = null,
        )
        val chat = createTestChat(
            TestChatParams(
                participants = listOf(
                    createTestParticipant(testUserId),
                    offlineParticipant,
                ),
                isOneToOne = true,
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertEquals(false, result.isOnline)
        assertNull(result.lastOnlineTime)
    }

    @Test
    fun `toChatListItemUiModel handles group chat online status`() {
        val chat = createTestChat(
            TestChatParams(
                participants = listOf(
                    createTestParticipant(testUserId),
                    createTestParticipant(testOtherUserId, onlineAt = testTimestamp),
                    createTestParticipant(ParticipantId(UUID.randomUUID())),
                ),
                isOneToOne = false,
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertEquals(false, result.isOnline)
        assertNull(result.lastOnlineTime)
    }

    @Test
    fun `toChatListItemUiModel takes last message from multiple messages`() {
        val firstMessage = createTestTextMessage(
            text = "First message",
            createdAt = testTimestamp,
        )
        val lastMessage = createTestTextMessage(
            text = "Last message",
            createdAt = testTimestamp.plus(kotlin.time.Duration.parse("1h")),
        )
        val chat = createTestChat(
            TestChatParams(
                messages = listOf(firstMessage, lastMessage),
                participants = listOf(
                    createTestParticipant(testUserId),
                    createTestParticipant(testOtherUserId),
                ),
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertEquals("Last message", result.lastMessage)
        assertEquals(lastMessage.createdAt, result.lastMessageTime)
    }

    // Note: Testing unsupported message types is complex due to Message interface
    // This would be better tested through integration tests

    @Test
    fun `toChatListItemUiModel handles chat with null picture URL`() {
        val chat = createTestChat(
            TestChatParams(
                pictureUrl = null,
                participants = listOf(
                    createTestParticipant(testUserId),
                    createTestParticipant(testOtherUserId),
                ),
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertNull(result.pictureUrl)
    }

    @Test
    fun `toChatListItemUiModel handles empty participant list for one-to-one chat`() {
        val chat = createTestChat(
            TestChatParams(
                participants = emptyList(),
                isOneToOne = true,
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertEquals(false, result.isOnline)
        assertNull(result.lastOnlineTime)
    }

    @Test
    fun `toChatListItemUiModel handles single participant for one-to-one chat`() {
        val chat = createTestChat(
            TestChatParams(
                participants = listOf(createTestParticipant(testUserId)),
                isOneToOne = true,
            ),
        )

        val result = chat.toChatListItemUiModel()

        assertEquals(false, result.isOnline)
        assertNull(result.lastOnlineTime)
    }

    @Test
    fun `ChatListUiState Empty and NotEmpty are correctly structured`() {
        val emptyState: ChatListUiState = ChatListUiState.Empty
        val notEmptyState: ChatListUiState = ChatListUiState.NotEmpty(persistentListOf())

        // Just ensure they can be created and used in when expressions
        val result = when (emptyState) {
            ChatListUiState.Empty -> "empty"
            is ChatListUiState.NotEmpty -> "not empty"
        }

        assertEquals("empty", result)

        // Test NotEmpty state
        val notEmptyResult = when (notEmptyState) {
            ChatListUiState.Empty -> "empty"
            is ChatListUiState.NotEmpty -> "not empty"
        }
        assertEquals("not empty", notEmptyResult)
    }

    @Test
    fun `ChatListItemUiModel data class works correctly`() {
        val uiModel = ChatListItemUiModel(
            id = testChatId,
            name = "Test",
            pictureUrl = null,
            lastMessage = "Test message",
            lastMessageTime = testTimestamp,
            unreadCount = 1,
            isOnline = true,
            lastOnlineTime = testTimestamp,
        )

        assertEquals(testChatId, uiModel.id)
        assertEquals("Test", uiModel.name)
        assertEquals("Test message", uiModel.lastMessage)
        assertEquals(1, uiModel.unreadCount)
        assertEquals(true, uiModel.isOnline)
    }
}
