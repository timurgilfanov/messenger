package timur.gilfanov.messenger.domain.entity.message

import kotlin.time.Duration

sealed class CanNotSendMessageError {
    data class DebouncingInProgress(val needToWait: Duration) : CanNotSendMessageError()
}
