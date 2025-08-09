@file:Suppress("TooManyFunctions") // DTO mapping utility functions

package timur.gilfanov.messenger.data.source.remote.dto

import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.datetime.Instant
import timur.gilfanov.messenger.domain.entity.chat.Chat
import timur.gilfanov.messenger.domain.entity.chat.ChatId
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule
import timur.gilfanov.messenger.domain.entity.chat.Participant
import timur.gilfanov.messenger.domain.entity.chat.ParticipantId
import timur.gilfanov.messenger.domain.entity.chat.Rule
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus
import timur.gilfanov.messenger.domain.entity.message.Message
import timur.gilfanov.messenger.domain.entity.message.MessageId
import timur.gilfanov.messenger.domain.entity.message.TextMessage
import timur.gilfanov.messenger.domain.usecase.message.DeleteMessageMode

/**
 * Mappers between network DTOs and domain entities.
 */

// Chat mappers
fun ChatDto.toDomain(): Chat = Chat(
    id = ChatId(UUID.fromString(id)),
    name = name,
    pictureUrl = pictureUrl,
    messages = messages.map { it.toDomain() }.toImmutableList(),
    participants = participants.map { it.toDomain() }.toImmutableSet(),
    rules = rules.map { it.toDomain() }.toImmutableSet(),
    unreadMessagesCount = unreadMessagesCount,
    lastReadMessageId = lastReadMessageId?.let { MessageId(UUID.fromString(it)) },
    isClosed = isClosed,
    isArchived = isArchived,
    isOneToOne = isOneToOne,
)

fun Chat.toDto(): ChatDto = ChatDto(
    id = id.id.toString(),
    name = name,
    pictureUrl = pictureUrl,
    messages = messages.map { it.toDto() },
    participants = participants.map { it.toDto() },
    rules = rules.map { it.toDto() },
    unreadMessagesCount = unreadMessagesCount,
    lastReadMessageId = lastReadMessageId?.id?.toString(),
    isClosed = isClosed,
    isArchived = isArchived,
    isOneToOne = isOneToOne,
)

fun Chat.toCreateRequest(): CreateChatRequestDto = CreateChatRequestDto(
    name = name,
    pictureUrl = pictureUrl,
    participants = participants.map { it.id.id.toString() },
    rules = rules.map { it.toDto() },
    isOneToOne = isOneToOne,
)

// Participant mappers
fun ParticipantDto.toDomain(): Participant = Participant(
    id = ParticipantId(UUID.fromString(id)),
    name = name,
    pictureUrl = pictureUrl,
    joinedAt = Instant.parse(joinedAt),
    onlineAt = onlineAt?.let { Instant.parse(it) },
    isAdmin = isAdmin,
    isModerator = isModerator,
)

fun Participant.toDto(): ParticipantDto = ParticipantDto(
    id = id.id.toString(),
    name = name,
    pictureUrl = pictureUrl,
    joinedAt = joinedAt.toString(),
    onlineAt = onlineAt?.toString(),
    isAdmin = isAdmin,
    isModerator = isModerator,
)

// Message mappers
fun MessageDto.toDomain(): Message {
    // Note: We need actual Participant object, not just ID
    // This is a simplified version - in real implementation, we'd need to fetch the participant
    val participant = Participant(
        id = ParticipantId(UUID.fromString(senderId)),
        name = "Unknown", // Would be populated from API response
        pictureUrl = null,
        joinedAt = Instant.parse(createdAt),
        onlineAt = null,
    )

    return TextMessage(
        id = MessageId(UUID.fromString(id)),
        parentId = null, // Not included in DTO for now
        sender = participant,
        recipient = ChatId(UUID.fromString(chatId)),
        createdAt = Instant.parse(createdAt),
        editedAt = editedAt?.let { Instant.parse(it) },
        deliveryStatus = deliveryStatus.toDomain(),
        text = content,
    )
}

fun Message.toDto(): MessageDto = MessageDto(
    id = id.id.toString(),
    chatId = recipient.id.toString(),
    senderId = sender.id.id.toString(),
    createdAt = createdAt.toString(),
    editedAt = editedAt?.toString(),
    content = when (this) {
        is TextMessage -> text
        else -> throw IllegalArgumentException("Unsupported message type: ${this::class}")
    },
    deliveryStatus = deliveryStatus?.toDto() ?: DeliveryStatusDto.Sent,
)

fun Message.toSendRequest(): SendMessageRequestDto = SendMessageRequestDto(
    chatId = recipient.id.toString(),
    content = when (this) {
        is TextMessage -> text
        else -> throw IllegalArgumentException("Unsupported message type: ${this::class}")
    },
)

fun Message.toEditRequest(): EditMessageRequestDto = EditMessageRequestDto(
    content = when (this) {
        is TextMessage -> text
        else -> throw IllegalArgumentException("Unsupported message type: ${this::class}")
    },
)

// DeliveryStatus mappers
fun DeliveryStatusDto.toDomain(): DeliveryStatus = when (this) {
    is DeliveryStatusDto.Sending -> DeliveryStatus.Sending(progress)
    is DeliveryStatusDto.Failed -> DeliveryStatus.Failed(DeliveryError.NetworkUnavailable)
    is DeliveryStatusDto.Sent -> DeliveryStatus.Sent
    is DeliveryStatusDto.Delivered -> DeliveryStatus.Delivered
    is DeliveryStatusDto.Read -> DeliveryStatus.Read
}

fun DeliveryStatus.toDto(): DeliveryStatusDto = when (this) {
    is DeliveryStatus.Sending -> DeliveryStatusDto.Sending(progress)
    is DeliveryStatus.Failed -> DeliveryStatusDto.Failed(reason.toString())
    is DeliveryStatus.Sent -> DeliveryStatusDto.Sent
    is DeliveryStatus.Delivered -> DeliveryStatusDto.Delivered
    is DeliveryStatus.Read -> DeliveryStatusDto.Read
}

// Rule mappers
fun RuleDto.toDomain(): Rule = when (this) {
    is RuleDto.CanNotWriteAfterJoining ->
        CreateMessageRule.CanNotWriteAfterJoining(durationMillis.milliseconds)
    is RuleDto.Debounce -> CreateMessageRule.Debounce(delayMillis.milliseconds)
    is RuleDto.EditWindow -> EditMessageRule.EditWindow(durationMillis.milliseconds)
    is RuleDto.SenderIdCanNotChange -> EditMessageRule.SenderIdCanNotChange
    is RuleDto.RecipientCanNotChange -> EditMessageRule.RecipientCanNotChange
    is RuleDto.CreationTimeCanNotChange -> EditMessageRule.CreationTimeCanNotChange
    is RuleDto.DeleteWindow -> DeleteMessageRule.DeleteWindow(durationMillis.milliseconds)
    is RuleDto.SenderCanDeleteOwn -> DeleteMessageRule.SenderCanDeleteOwn
    is RuleDto.AdminCanDeleteAny -> DeleteMessageRule.AdminCanDeleteAny
    is RuleDto.ModeratorCanDeleteAny -> DeleteMessageRule.ModeratorCanDeleteAny
    is RuleDto.NoDeleteAfterDelivered -> DeleteMessageRule.NoDeleteAfterDelivered
    is RuleDto.DeleteForEveryoneWindow ->
        DeleteMessageRule.DeleteForEveryoneWindow(durationMillis.milliseconds)
    is RuleDto.OnlyAdminCanDelete -> DeleteChatRule.OnlyAdminCanDelete
}

fun Rule.toDto(): RuleDto = when (this) {
    is CreateMessageRule.CanNotWriteAfterJoining ->
        RuleDto.CanNotWriteAfterJoining(duration.inWholeMilliseconds)
    is CreateMessageRule.Debounce -> RuleDto.Debounce(delay.inWholeMilliseconds)
    is EditMessageRule.EditWindow -> RuleDto.EditWindow(duration.inWholeMilliseconds)
    is EditMessageRule.SenderIdCanNotChange -> RuleDto.SenderIdCanNotChange
    is EditMessageRule.RecipientCanNotChange -> RuleDto.RecipientCanNotChange
    is EditMessageRule.CreationTimeCanNotChange -> RuleDto.CreationTimeCanNotChange
    is DeleteMessageRule.DeleteWindow -> RuleDto.DeleteWindow(duration.inWholeMilliseconds)
    is DeleteMessageRule.SenderCanDeleteOwn -> RuleDto.SenderCanDeleteOwn
    is DeleteMessageRule.AdminCanDeleteAny -> RuleDto.AdminCanDeleteAny
    is DeleteMessageRule.ModeratorCanDeleteAny -> RuleDto.ModeratorCanDeleteAny
    is DeleteMessageRule.NoDeleteAfterDelivered -> RuleDto.NoDeleteAfterDelivered
    is DeleteMessageRule.DeleteForEveryoneWindow ->
        RuleDto.DeleteForEveryoneWindow(duration.inWholeMilliseconds)
    is DeleteChatRule.OnlyAdminCanDelete -> RuleDto.OnlyAdminCanDelete
}

// DeleteMessageMode mappers
fun DeleteMessageMode.toRequestDto(): DeleteMessageRequestDto = DeleteMessageRequestDto(
    mode = when (this) {
        DeleteMessageMode.FOR_SENDER_ONLY -> "FOR_SENDER"
        DeleteMessageMode.FOR_EVERYONE -> "FOR_EVERYONE"
    },
)
