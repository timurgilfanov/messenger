package timur.gilfanov.messenger.domain.entity.chat

import kotlin.time.Duration

sealed class Rule {
    data class CanNotWriteAfterJoining(val duration: Duration) : Rule()
    data class Debounce(val delay: Duration) : Rule()
}
