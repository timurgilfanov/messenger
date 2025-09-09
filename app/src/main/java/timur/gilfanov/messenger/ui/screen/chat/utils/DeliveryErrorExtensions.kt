package timur.gilfanov.messenger.ui.screen.chat.utils

import android.content.Context
import kotlin.time.Duration
import timur.gilfanov.messenger.R
import timur.gilfanov.messenger.domain.entity.message.DeliveryError

fun DeliveryError.toLocalizedString(context: Context): String = when (this) {
    is DeliveryError.NetworkUnavailable -> context.getString(
        R.string.delivery_error_network_unavailable,
    )
    is DeliveryError.ServerUnreachable -> context.getString(
        R.string.delivery_error_server_unreachable,
    )
    is DeliveryError.MessageTooLarge -> context.getString(
        R.string.delivery_error_message_too_large,
    )
    is DeliveryError.RecipientBlocked -> context.getString(
        R.string.delivery_error_recipient_blocked,
    )
    is DeliveryError.RecipientNotFound -> context.getString(
        R.string.delivery_error_recipient_not_found,
    )
    is DeliveryError.MessageExpired -> context.getString(
        R.string.delivery_error_message_expired,
    )
    is DeliveryError.RateLimitExceeded -> {
        val formattedDuration = formatDuration(retryAfter)
        context.getString(R.string.delivery_error_rate_limit_exceeded, formattedDuration)
    }
    is DeliveryError.UnknownError -> {
        if (message != null) {
            "$message (Error $errorCode)"
        } else {
            context.getString(R.string.delivery_error_unknown)
        }
    }
}

@Suppress("MagicNumber")
private fun formatDuration(duration: Duration): String = when {
    duration.inWholeHours > 0 -> "${duration.inWholeHours}h ${duration.inWholeMinutes % 60}m"
    duration.inWholeMinutes > 0 -> "${duration.inWholeMinutes}m"
    else -> "${duration.inWholeSeconds}s"
}
