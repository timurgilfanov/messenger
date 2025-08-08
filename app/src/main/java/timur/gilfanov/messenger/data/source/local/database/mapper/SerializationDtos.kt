package timur.gilfanov.messenger.data.source.local.database.mapper

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.Serializable
import timur.gilfanov.messenger.domain.entity.chat.CreateMessageRule
import timur.gilfanov.messenger.domain.entity.chat.DeleteChatRule
import timur.gilfanov.messenger.domain.entity.chat.DeleteMessageRule
import timur.gilfanov.messenger.domain.entity.chat.EditMessageRule
import timur.gilfanov.messenger.domain.entity.chat.Rule
import timur.gilfanov.messenger.domain.entity.message.DeliveryError
import timur.gilfanov.messenger.domain.entity.message.DeliveryStatus

/**
 * DTOs for serializing domain objects to JSON strings for database storage.
 */

@Serializable
sealed class RuleDto {
    @Serializable
    data class CanNotWriteAfterJoining(val durationMillis: Long) : RuleDto()

    @Serializable
    data class Debounce(val delayMillis: Long) : RuleDto()

    @Serializable
    data class EditWindow(val durationMillis: Long) : RuleDto()

    @Serializable
    object SenderIdCanNotChange : RuleDto()

    @Serializable
    object RecipientCanNotChange : RuleDto()

    @Serializable
    object CreationTimeCanNotChange : RuleDto()

    @Serializable
    data class DeleteWindow(val durationMillis: Long) : RuleDto()

    @Serializable
    object SenderCanDeleteOwn : RuleDto()

    @Serializable
    object AdminCanDeleteAny : RuleDto()

    @Serializable
    object ModeratorCanDeleteAny : RuleDto()

    @Serializable
    object NoDeleteAfterDelivered : RuleDto()

    @Serializable
    data class DeleteForEveryoneWindow(val durationMillis: Long) : RuleDto()

    @Serializable
    object OnlyAdminCanDelete : RuleDto()
}

@Serializable
sealed class DeliveryStatusDto {
    @Serializable
    data class Sending(val progress: Int) : DeliveryStatusDto()

    @Serializable
    data class Failed(val reason: String) : DeliveryStatusDto()

    @Serializable
    object Sent : DeliveryStatusDto()

    @Serializable
    object Delivered : DeliveryStatusDto()

    @Serializable
    object Read : DeliveryStatusDto()
}

/**
 * Extension functions to convert between domain objects and DTOs.
 */
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

fun DeliveryStatus.toDto(): DeliveryStatusDto = when (this) {
    is DeliveryStatus.Sending -> DeliveryStatusDto.Sending(progress)
    is DeliveryStatus.Failed -> DeliveryStatusDto.Failed(reason.toString())
    is DeliveryStatus.Sent -> DeliveryStatusDto.Sent
    is DeliveryStatus.Delivered -> DeliveryStatusDto.Delivered
    is DeliveryStatus.Read -> DeliveryStatusDto.Read
}

fun DeliveryStatusDto.toDomain(): DeliveryStatus = when (this) {
    is DeliveryStatusDto.Sending -> DeliveryStatus.Sending(progress)
    is DeliveryStatusDto.Failed -> DeliveryStatus.Failed(
        // For now, we'll map all failures to NetworkUnavailable
        // This can be improved to parse the reason string and map to specific errors
        DeliveryError.NetworkUnavailable,
    )

    is DeliveryStatusDto.Sent -> DeliveryStatus.Sent
    is DeliveryStatusDto.Delivered -> DeliveryStatus.Delivered
    is DeliveryStatusDto.Read -> DeliveryStatus.Read
}
