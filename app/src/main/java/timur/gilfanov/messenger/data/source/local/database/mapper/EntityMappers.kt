package timur.gilfanov.messenger.data.source.local.database.mapper

import java.util.UUID
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timur.gilfanov.messenger.data.source.local.database.entity.ChatEntity
import timur.gilfanov.messenger.data.source.local.database.entity.ChatParticipantCrossRef
import timur.gilfanov.messenger.data.source.local.database.entity.ChatWithParticipantsAndMessages
import timur.gilfanov.messenger.data.source.local.database.entity.MessageEntity
import timur.gilfanov.messenger.data.source.local.database.entity.MessageType
import timur.gilfanov.messenger.data.source.local.database.entity.ParticipantEntity
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.ChatPreview
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage

/**
 * Mappers for converting between domain models and Room entities.
 */
object EntityMappers {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    /**
     * Maps Chat domain model to ChatEntity and related entities.
     */
    fun Chat.toChatEntity(): ChatEntity = ChatEntity(
        id = id.id.toString(),
        name = name,
        pictureUrl = pictureUrl,
        rules = json.encodeToString(rules.map { it.toDto() }),
        unreadMessagesCount = unreadMessagesCount,
        lastReadMessageId = lastReadMessageId?.id?.toString(),
        updatedAt = messages.maxOfOrNull { it.createdAt },
    )

    /**
     * Maps Participant domain model to ParticipantEntity (global identity only).
     */
    fun Participant.toParticipantEntity(): ParticipantEntity = ParticipantEntity(
        id = id.id.toString(),
        name = name,
        pictureUrl = pictureUrl,
        onlineAt = onlineAt,
    )

    /**
     * Maps Participant domain model to ChatParticipantCrossRef (chat-specific properties).
     */
    fun Participant.toChatParticipantCrossRef(chatId: String): ChatParticipantCrossRef =
        ChatParticipantCrossRef(
            chatId = chatId,
            participantId = id.id.toString(),
            joinedAt = joinedAt,
            isAdmin = isAdmin,
            isModerator = isModerator,
        )

    /**
     * Maps Message domain model to MessageEntity.
     */
    fun Message.toMessageEntity(): MessageEntity {
        // Currently only TextMessage is supported
        return when (this) {
            is TextMessage -> MessageEntity(
                id = id.id.toString(),
                chatId = recipient.id.toString(),
                senderId = sender.id.id.toString(),
                parentId = parentId?.id?.toString(),
                type = MessageType.TEXT,
                text = text,
                imageUrl = null,
                deliveryStatus = deliveryStatus?.let { json.encodeToString(it.toDto()) },
                createdAt = createdAt,
                sentAt = sentAt,
                deliveredAt = deliveredAt,
                editedAt = editedAt,
            )
            else -> throw IllegalArgumentException("Unsupported message type: ${this::class}")
        }
    }

    /**
     * Creates ChatParticipantCrossRef entries for a chat.
     */
    fun createChatParticipantCrossRefs(
        chatId: ChatId,
        participants: Set<Participant>,
    ): List<ChatParticipantCrossRef> = participants.map { participant ->
        ChatParticipantCrossRef(
            chatId = chatId.id.toString(),
            participantId = participant.id.id.toString(),
            joinedAt = participant.joinedAt,
            isAdmin = participant.isAdmin,
            isModerator = participant.isModerator,
        )
    }

    /**
     * Maps ChatWithParticipantsAndMessages to Chat domain model.
     */
    fun ChatWithParticipantsAndMessages.toChat(): Chat = Chat(
        id = ChatId(UUID.fromString(chat.id)),
        name = chat.name,
        pictureUrl = chat.pictureUrl,
        messages = messages.map {
            it.toMessage(participants, participantCrossRefs)
        }.toImmutableList(),
        participants = participants.mapIndexed { index, participant ->
            val crossRef = participantCrossRefs.find { it.participantId == participant.id }
                ?: error("No cross-reference found for participant ${participant.id}")
            participant.toParticipant(crossRef)
        }.toImmutableSet(),
        rules = chat.rules.let { rulesJson ->
            json.decodeFromString<List<RuleDto>>(rulesJson).map { it.toDomain() }.toImmutableSet()
        },
        unreadMessagesCount = chat.unreadMessagesCount,
        lastReadMessageId = chat.lastReadMessageId?.let { MessageId(UUID.fromString(it)) },
        isClosed = false, // Default value, can be extended with a field in ChatEntity
        isArchived = false, // Default value, can be extended with a field in ChatEntity
        isOneToOne = participants.size == 2, // Inferred from participant count
    )

    /**
     * Maps ChatWithParticipantsAndMessages to ChatPreview domain model.
     */
    fun ChatWithParticipantsAndMessages.toChatPreview(): ChatPreview {
        val lastMessage = messages.maxByOrNull { it.createdAt }
        return ChatPreview(
            id = ChatId(UUID.fromString(chat.id)),
            participants = participants.map { participant ->
                val crossRef = participantCrossRefs.find { it.participantId == participant.id }
                    ?: error("No cross-reference found for participant ${participant.id}")
                participant.toParticipant(crossRef)
            }.toImmutableSet(),
            name = chat.name,
            pictureUrl = chat.pictureUrl,
            rules = chat.rules.let { rulesJson ->
                json.decodeFromString<List<RuleDto>>(rulesJson).map {
                    it.toDomain()
                }.toImmutableSet()
            },
            unreadMessagesCount = chat.unreadMessagesCount,
            lastReadMessageId = chat.lastReadMessageId?.let { MessageId(UUID.fromString(it)) },
            lastMessage = lastMessage?.toMessage(participants, participantCrossRefs),
            lastActivityAt = lastMessage?.createdAt,
        )
    }

    /**
     * Maps ParticipantEntity to Participant domain model.
     * Requires ChatParticipantCrossRef for chat-specific properties.
     */
    fun ParticipantEntity.toParticipant(crossRef: ChatParticipantCrossRef): Participant =
        Participant(
            id = ParticipantId(UUID.fromString(id)),
            name = name,
            pictureUrl = pictureUrl,
            joinedAt = crossRef.joinedAt,
            onlineAt = onlineAt,
            isAdmin = crossRef.isAdmin,
            isModerator = crossRef.isModerator,
        )

    /**
     * Maps MessageEntity to Message domain model.
     * Requires participants list and cross-references to find the sender.
     */
    fun MessageEntity.toMessage(
        participants: List<ParticipantEntity>,
        participantCrossRefs: List<ChatParticipantCrossRef>,
    ): Message {
        val senderEntity = participants.find { it.id == senderId }
            ?: error("Sender not found for message $id")
        val senderCrossRef = participantCrossRefs.find { it.participantId == senderId }
            ?: error("Sender cross-reference not found for message $id")
        val sender = senderEntity.toParticipant(senderCrossRef)

        val deliveryStatus = deliveryStatus?.let {
            json.decodeFromString<DeliveryStatusDto>(it).toDomain()
        }

        return when (type) {
            MessageType.TEXT -> TextMessage(
                id = MessageId(UUID.fromString(id)),
                parentId = parentId?.let { MessageId(UUID.fromString(it)) },
                sender = sender,
                recipient = ChatId(UUID.fromString(chatId)),
                createdAt = createdAt,
                sentAt = sentAt,
                deliveredAt = deliveredAt,
                editedAt = editedAt,
                deliveryStatus = deliveryStatus,
                text = text ?: "",
            )
            MessageType.IMAGE -> {
                // For now, treat image messages as text messages with image URL as text
                // This can be improved when ImageMessage is added to domain
                TextMessage(
                    id = MessageId(UUID.fromString(id)),
                    parentId = parentId?.let { MessageId(UUID.fromString(it)) },
                    sender = sender,
                    recipient = ChatId(UUID.fromString(chatId)),
                    createdAt = createdAt,
                    sentAt = sentAt,
                    deliveredAt = deliveredAt,
                    editedAt = editedAt,
                    deliveryStatus = deliveryStatus,
                    text = imageUrl ?: "[Image]",
                )
            }
        }
    }
}
