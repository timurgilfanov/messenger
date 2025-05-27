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
