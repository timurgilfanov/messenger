package timur.gilfanov.messenger.domain.entity.message

import kotlin.time.Duration

sealed class DeliveryError {
    object NetworkUnavailable : DeliveryError()
    object ServerUnreachable : DeliveryError()
    object MessageTooLarge : DeliveryError()
    object RecipientBlocked : DeliveryError()
    object RecipientNotFound : DeliveryError()
    object MessageExpired : DeliveryError()
    data class RateLimitExceeded(val retryAfter: Duration) : DeliveryError()
    data class UnknownError(val errorCode: Int, val message: String? = null) : DeliveryError()
}
