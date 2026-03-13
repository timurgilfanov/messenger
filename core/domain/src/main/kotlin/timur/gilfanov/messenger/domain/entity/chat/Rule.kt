package timur.gilfanov.messenger.domain.entity.chat

import kotlin.time.Duration

sealed class Rule

sealed class CreateMessageRule : Rule() {
    data class CanNotWriteAfterJoining(val duration: Duration) : CreateMessageRule()
    data class Debounce(val delay: Duration) : CreateMessageRule()
}

sealed class EditMessageRule : Rule() {
    data class EditWindow(val duration: Duration) : EditMessageRule()
    object SenderIdCanNotChange : EditMessageRule()
    object RecipientCanNotChange : EditMessageRule()
    object CreationTimeCanNotChange : EditMessageRule()
}

sealed class DeleteMessageRule : Rule() {
    data class DeleteWindow(val duration: Duration) : DeleteMessageRule()
    object SenderCanDeleteOwn : DeleteMessageRule()
    object AdminCanDeleteAny : DeleteMessageRule()
    object ModeratorCanDeleteAny : DeleteMessageRule()
    object NoDeleteAfterDelivered : DeleteMessageRule()
    data class DeleteForEveryoneWindow(val duration: Duration) : DeleteMessageRule()
}

sealed class DeleteChatRule : Rule() {
    object OnlyAdminCanDelete : DeleteChatRule()
}
