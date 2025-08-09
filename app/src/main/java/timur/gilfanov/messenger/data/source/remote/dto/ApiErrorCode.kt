package timur.gilfanov.messenger.data.source.remote.dto

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type-safe API error codes using sealed class for maximum flexibility.
 * Supports both simple error codes and error codes with additional data.
 */
sealed class ApiErrorCode {
    @Serializable
    @SerialName("CHAT_NOT_FOUND")
    data object ChatNotFound : ApiErrorCode()

    @Serializable
    @SerialName("MESSAGE_NOT_FOUND")
    data object MessageNotFound : ApiErrorCode()

    @Serializable
    @SerialName("INVALID_INVITE_LINK")
    data object InvalidInviteLink : ApiErrorCode()

    @Serializable
    @SerialName("EXPIRED_INVITE_LINK")
    data object ExpiredInviteLink : ApiErrorCode()

    @Serializable
    @SerialName("CHAT_CLOSED")
    data object ChatClosed : ApiErrorCode()

    @Serializable
    @SerialName("ALREADY_JOINED")
    data object AlreadyJoined : ApiErrorCode()

    @Serializable
    @SerialName("CHAT_FULL")
    data object ChatFull : ApiErrorCode()

    @Serializable
    @SerialName("USER_BLOCKED")
    data object UserBlocked : ApiErrorCode()

    @Serializable
    @SerialName("RATE_LIMIT_EXCEEDED")
    data object RateLimitExceeded : ApiErrorCode()

    @Serializable
    @SerialName("COOLDOWN_ACTIVE")
    data class CooldownActive(val remainingMs: Long) : ApiErrorCode() {
        val remaining: Duration get() = remainingMs.milliseconds
    }

    @Serializable
    @SerialName("UNAUTHORIZED")
    data object Unauthorized : ApiErrorCode()

    @Serializable
    @SerialName("NETWORK_NOT_AVAILABLE")
    data object NetworkNotAvailable : ApiErrorCode()

    @Serializable
    @SerialName("SERVER_UNREACHABLE")
    data object ServerUnreachable : ApiErrorCode()

    @Serializable
    @SerialName("SERVER_ERROR")
    data object ServerError : ApiErrorCode()

    @Serializable
    @SerialName("UNKNOWN")
    data class Unknown(val originalCode: String) : ApiErrorCode()
}
