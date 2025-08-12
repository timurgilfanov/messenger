package timur.gilfanov.messenger.domain.testutil

import java.util.UUID
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.buildChat
import timur.gilfanov.messenger.domain.entity.chat.buildParticipant
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.entity.message.buildTextMessage

object DomainTestFixtures {

    data class TestChatParams(
        val id: ChatId = ChatId(UUID.randomUUID()),
        val name: String = "Test Chat",
        val participants: Set<Participant> = setOf(),
        val messages: List<Message> = emptyList(),
        val isOneToOne: Boolean = false,
        val pictureUrl: String? = null,
        val unreadMessagesCount: Int = 0,
        val lastReadMessageId: MessageId? = null,
    )

    fun createTestChat(params: TestChatParams): Chat = buildChat {
        this.id = params.id
        this.name = params.name
        this.participants = if (params.participants.isEmpty()) {
            persistentSetOf<Participant>().add(createTestParticipant())
        } else {
            persistentSetOf<Participant>().addAll(params.participants)
        }
        this.messages = persistentListOf<Message>().addAll(params.messages)
        this.isOneToOne = params.isOneToOne
        this.pictureUrl = params.pictureUrl
        this.unreadMessagesCount = params.unreadMessagesCount
        this.lastReadMessageId = params.lastReadMessageId
        this.rules = persistentSetOf()
    }

    fun createTestParticipant(
        id: ParticipantId = ParticipantId(UUID.randomUUID()),
        name: String = "Test User",
        pictureUrl: String? = null,
        joinedAt: Instant = Instant.fromEpochMilliseconds(1000),
        onlineAt: Instant? = Instant.fromEpochMilliseconds(1000),
    ): Participant = buildParticipant {
        this.id = id
        this.name = name
        this.pictureUrl = pictureUrl
        this.joinedAt = joinedAt
        this.onlineAt = onlineAt
    }

    data class TestMessageParams(
        val id: MessageId = MessageId(UUID.randomUUID()),
        val text: String = "Test message",
        val sender: Participant = createTestParticipant(),
        val recipient: ChatId = ChatId(UUID.randomUUID()),
        val deliveryStatus: DeliveryStatus = DeliveryStatus.Sent,
        val createdAt: Instant = Instant.fromEpochMilliseconds(2000),
        val parentId: MessageId? = null,
    )

    fun createTestTextMessage(params: TestMessageParams): TextMessage = buildTextMessage {
        this.id = params.id
        this.text = params.text
        this.sender = params.sender
        this.recipient = params.recipient
        this.deliveryStatus = params.deliveryStatus
        this.createdAt = params.createdAt
        this.parentId = params.parentId
    }

    data class TestChatWithParticipantsParams(
        val chatId: ChatId = ChatId(UUID.randomUUID()),
        val currentUserId: ParticipantId = ParticipantId(UUID.randomUUID()),
        val otherUserIds: List<ParticipantId> = listOf(ParticipantId(UUID.randomUUID())),
        val messages: List<Message> = emptyList(),
        val isOneToOne: Boolean = true,
        val chatName: String = "Direct Message",
    )

    fun createTestChatWithParticipants(params: TestChatWithParticipantsParams): Chat {
        val currentUser = createTestParticipant(id = params.currentUserId, name = "Current User")
        val otherUsers = if (params.otherUserIds.size == 1) {
            listOf(
                createTestParticipant(
                    id = params.otherUserIds[0],
                    name = "Other User",
                    onlineAt = null,
                ),
            )
        } else {
            params.otherUserIds.mapIndexed { index, userId ->
                createTestParticipant(id = userId, name = "User ${index + 1}", onlineAt = null)
            }
        }

        return createTestChat(
            TestChatParams(
                id = params.chatId,
                name = params.chatName,
                participants = setOf(currentUser) + otherUsers,
                messages = params.messages,
                isOneToOne = params.isOneToOne,
            ),
        )
    }

    // Convenience overloads for backward compatibility
    @Suppress("LongParameterList")
    fun createTestChat(
        id: ChatId = ChatId(UUID.randomUUID()),
        name: String = "Test Chat",
        participants: Set<Participant> = setOf(),
        messages: List<Message> = emptyList(),
        isOneToOne: Boolean = false,
        pictureUrl: String? = null,
        unreadMessagesCount: Int = 0,
        lastReadMessageId: MessageId? = null,
    ): Chat = createTestChat(
        TestChatParams(
            id,
            name,
            participants,
            messages,
            isOneToOne,
            pictureUrl,
            unreadMessagesCount,
            lastReadMessageId,
        ),
    )

    @Suppress("LongParameterList")
    fun createTestTextMessage(
        id: MessageId = MessageId(UUID.randomUUID()),
        text: String = "Test message",
        sender: Participant = createTestParticipant(),
        recipient: ChatId = ChatId(UUID.randomUUID()),
        deliveryStatus: DeliveryStatus = DeliveryStatus.Sent,
        createdAt: Instant = Instant.fromEpochMilliseconds(2000),
        parentId: MessageId? = null,
    ): TextMessage = createTestTextMessage(
        TestMessageParams(id, text, sender, recipient, deliveryStatus, createdAt, parentId),
    )

    @Suppress("LongParameterList")
    fun createTestChatWithParticipants(
        chatId: ChatId = ChatId(UUID.randomUUID()),
        currentUserId: ParticipantId = ParticipantId(UUID.randomUUID()),
        otherUserIds: List<ParticipantId> = listOf(ParticipantId(UUID.randomUUID())),
        messages: List<Message> = emptyList(),
        isOneToOne: Boolean = otherUserIds.size == 1,
        chatName: String = if (isOneToOne) "Direct Message" else "Group Chat",
    ): Chat = createTestChatWithParticipants(
        TestChatWithParticipantsParams(
            chatId,
            currentUserId,
            otherUserIds,
            messages,
            isOneToOne,
            chatName,
        ),
    )
}
